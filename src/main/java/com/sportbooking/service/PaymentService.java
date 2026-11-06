package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.response.PaymentResultResponse;
import com.sportbooking.dto.response.PaymentUrlResponse;
import com.sportbooking.entity.Booking;
import com.sportbooking.entity.Payment;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.entity.enums.PaymentStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.integration.vnpay.VnPayGateway;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Tạo link thanh toán và hiển thị kết quả khi khách quay về.
 * Việc ĐỔI TRẠNG THÁI sau thanh toán chỉ nằm ở VnPayIpnService.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final VnPayGateway vnPayGateway;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    /**
     * Mỗi lần gọi tạo 1 Payment PENDING mới với txn_ref mới (khách thanh toán lỗi có thể thử lại).
     * Lưu Payment vào DB TRƯỚC khi đưa link cho khách: IPN tới thì luôn tìm thấy giao dịch.
     */
    @Transactional
    public PaymentUrlResponse createVnPayPayment(Long customerId, Long bookingId, String clientIp) {
        if (!vnPayGateway.isConfigured()) {
            throw new AppException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        Booking booking = bookingRepository.findByIdAndCustomerId(bookingId, customerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lượt đặt id=" + bookingId));

        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_BOOKING_STATE,
                    "Chỉ thanh toán được lượt đặt đang chờ thanh toán (hiện tại: " + booking.getStatus() + ")");
        }
        if (booking.isOverdue(now)) {
            throw new AppException(ErrorCode.INVALID_BOOKING_STATE, "Lượt đặt đã hết thời gian giữ chỗ, vui lòng đặt lại");
        }

        // Mã giao dịch: mã đặt sân + 6 ký tự ngẫu nhiên, chỉ chữ và số, duy nhất (UNIQUE uk_payments_txn_ref)
        String txnRef = booking.getBookingCode() + BookingCodeGenerator.randomChars(6);
        paymentRepository.save(Payment.newVnPay(booking, txnRef, booking.getTotalPrice()));

        // Hạn thanh toán bên VNPay = đúng hạn giữ chỗ: hết giữ chỗ thì VNPay cũng không nhận tiền nữa
        LocalDateTime createDate = LocalDateTime.ofInstant(now, bookingProperties.zone());
        LocalDateTime expireDate = LocalDateTime.ofInstant(booking.getExpiresAt(), bookingProperties.zone());
        String paymentUrl = vnPayGateway.buildPaymentUrl(txnRef, booking.getTotalPrice(),
                "Thanh toan dat san " + booking.getBookingCode(), clientIp, createDate, expireDate);

        return new PaymentUrlResponse(txnRef, booking.getTotalPrice(), paymentUrl, booking.getExpiresAt());
    }

    /**
     * Trình duyệt quay về sau khi thanh toán. CHỈ ĐỌC, KHÔNG ĐỔI TRẠNG THÁI. Vì sao?
     *  - Khách có thể đóng tab trước khi quay về -> nếu cập nhật ở đây, tiền đã trừ mà sân không được xác nhận.
     *  - Dữ liệu đi qua trình duyệt của khách. Có chữ ký nên không sửa được, nhưng IPN (server VNPay gọi
     *    thẳng server mình, có cơ chế gửi lại) mới là kênh đáng tin để ghi nhận tiền.
     */
    @Transactional(readOnly = true)
    public PaymentResultResponse getReturnResult(Map<String, String> params) {
        if (!vnPayGateway.verifySignature(params)) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_SIGNATURE);
        }
        Payment payment = paymentRepository.findByTxnRef(params.get("vnp_TxnRef"))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy giao dịch"));
        Booking booking = payment.getBooking();
        String responseCode = params.get("vnp_ResponseCode");

        String message;
        if (booking.getStatus() == BookingStatus.CONFIRMED && payment.getStatus() == PaymentStatus.SUCCESS) {
            message = "Thanh toán thành công, lượt đặt sân đã được xác nhận";
        } else if ("00".equals(responseCode) && payment.isPending()) {
            message = "VNPay báo thanh toán thành công, hệ thống đang chờ xác nhận. Vui lòng kiểm tra lại sau ít phút";
        } else if ("00".equals(responseCode)) {
            message = "Đã nhận thanh toán nhưng lượt đặt không còn hiệu lực (" + booking.getStatus()
                    + "). Chúng tôi sẽ liên hệ hoàn tiền";
        } else {
            message = "Thanh toán không thành công (mã " + responseCode + "). Bạn có thể thử lại trước khi hết hạn giữ chỗ";
        }
        return new PaymentResultResponse(booking.getBookingCode(), payment.getTxnRef(), responseCode,
                payment.getStatus(), booking.getStatus(), message);
    }
}
