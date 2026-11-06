package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.entity.Booking;
import com.sportbooking.entity.Payment;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.event.BookingConfirmedEvent;
import com.sportbooking.event.BookingEmailData;
import com.sportbooking.integration.vnpay.VnPayGateway;
import com.sportbooking.integration.vnpay.VnPayIpnResponse;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Xử lý IPN: máy chủ VNPay gọi thẳng tới server mình để báo kết quả giao dịch.
 * ĐÂY LÀ NƠI DUY NHẤT ghi nhận tiền và xác nhận lượt đặt.
 *
 * Thứ tự kiểm tra theo tài liệu VNPay:
 *   1. Chữ ký                      -> sai: 97
 *   2. Tìm giao dịch theo txn_ref  -> không có: 01
 *   3. Số tiền khớp                -> lệch: 04
 *   4. Giao dịch còn chờ xử lý?    -> đã xử lý rồi: 02  (IDEMPOTENT: nhận lại IPN cũ không làm gì thêm)
 *   5. Cập nhật                    -> 00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VnPayIpnService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final VnPayGateway vnPayGateway;
    private final BookingProperties bookingProperties;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Dùng TransactionTemplate (transaction viết bằng code) thay vì @Transactional:
     * lỗi optimistic lock có thể nổ ra lúc COMMIT, tức là ở ngoài method @Transactional.
     * Bọc try/catch BÊN NGOÀI transaction thì bắt được cả lỗi lúc commit và luôn trả JSON đúng định dạng VNPay.
     */
    public VnPayIpnResponse handleIpn(Map<String, String> params) {
        try {
            return transactionTemplate.execute(status -> process(params));
        } catch (OptimisticLockingFailureException e) {
            // Job hết hạn (hoặc khách hủy) vừa sửa booking đúng lúc này. Toàn bộ thay đổi đã rollback,
            // payment vẫn PENDING. Trả 99 -> VNPay gửi lại IPN sau 5 phút -> lần sau xử lý với trạng thái mới.
            log.warn("IPN for {} hit a concurrent booking update, asking VNPay to retry", params.get("vnp_TxnRef"));
            return VnPayIpnResponse.UNKNOWN_ERROR;
        } catch (RuntimeException e) {
            log.error("Failed to process VNPay IPN {}", params.get("vnp_TxnRef"), e);
            return VnPayIpnResponse.UNKNOWN_ERROR;
        }
    }

    private VnPayIpnResponse process(Map<String, String> params) {
        // 1. Chữ ký: kiểm tra TRƯỚC mọi thứ, không đụng DB nếu dữ liệu giả mạo
        if (!vnPayGateway.verifySignature(params)) {
            log.warn("IPN with invalid signature, txnRef={}", params.get("vnp_TxnRef"));
            return VnPayIpnResponse.INVALID_SIGNATURE;
        }

        // 2. Giao dịch (khóa dòng: IPN trùng lặp phải xếp hàng)
        Payment payment = paymentRepository.findByTxnRefForUpdate(params.get("vnp_TxnRef")).orElse(null);
        if (payment == null) {
            return VnPayIpnResponse.ORDER_NOT_FOUND;
        }

        // 3. Số tiền (VNPay gửi số tiền x 100)
        if (!amountMatches(params.get("vnp_Amount"), payment.getAmount())) {
            log.warn("IPN amount mismatch for {}: vnp_Amount={}, expected={}",
                    payment.getTxnRef(), params.get("vnp_Amount"), payment.getAmount() * 100);
            return VnPayIpnResponse.INVALID_AMOUNT;
        }

        // 4. Idempotency: IPN gửi lại cho giao dịch đã xử lý -> không làm gì nữa
        if (!payment.isPending()) {
            return VnPayIpnResponse.ALREADY_CONFIRMED;
        }

        // 5. Cập nhật
        String responseCode = params.get("vnp_ResponseCode");
        String providerTxnNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        Map<String, String> raw = new TreeMap<>(params);
        boolean paid = "00".equals(responseCode) && "00".equals(params.get("vnp_TransactionStatus"));

        if (!paid) {
            // Khách hủy, thẻ lỗi... Lượt đặt vẫn PENDING, khách có thể thử lại cho tới khi hết hạn giữ chỗ.
            payment.markFailed(providerTxnNo, bankCode, responseCode, raw);
            return VnPayIpnResponse.CONFIRM_SUCCESS;
        }

        Instant paidAt = parsePayDate(params.get("vnp_PayDate"));
        Booking booking = payment.getBooking();
        if (booking.getStatus() == BookingStatus.PENDING) {
            // Vẫn đang giữ chỗ (kể cả khi vừa quá hạn mà job chưa kịp chạy: slot vẫn còn của khách)
            booking.confirm();
            payment.markSuccess(providerTxnNo, bankCode, responseCode, paidAt, raw);
            // Ghi booking ngay: nếu job hết hạn vừa sửa booking này, lỗi optimistic lock nổ ra tại đây
            bookingRepository.flush();
            // Email xác nhận chỉ được gửi SAU KHI transaction này commit (xem BookingEmailListener)
            eventPublisher.publishEvent(new BookingConfirmedEvent(BookingEmailData.from(booking)));
            log.info("Booking {} confirmed by payment {}", booking.getBookingCode(), payment.getTxnRef());
        } else {
            // Đã nhận tiền nhưng lượt đặt không còn giữ chỗ (EXPIRED / CANCELLED / đã CONFIRMED bằng giao dịch khác).
            // Không thể "hồi sinh" lượt đặt vì slot có thể đã thuộc về người khác -> đánh dấu cần hoàn tiền.
            payment.markRefundRequired(providerTxnNo, bankCode, responseCode, paidAt, raw);
            log.warn("Payment {} succeeded but booking {} is {}: REFUND REQUIRED",
                    payment.getTxnRef(), booking.getBookingCode(), booking.getStatus());
        }
        // Trả 00 cho cả trường hợp cần hoàn tiền: ta ĐÃ ghi nhận giao dịch, VNPay không cần gửi lại.
        return VnPayIpnResponse.CONFIRM_SUCCESS;
    }

    private static boolean amountMatches(String vnpAmount, long expectedVnd) {
        try {
            long value = Long.parseLong(vnpAmount);
            return value % 100 == 0 && value / 100 == expectedVnd;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Instant parsePayDate(String payDate) {
        try {
            return LocalDateTime.parse(payDate, VnPayGateway.VNPAY_TIME).atZone(bookingProperties.zone()).toInstant();
        } catch (DateTimeParseException | NullPointerException e) {
            return clock.instant();
        }
    }
}
