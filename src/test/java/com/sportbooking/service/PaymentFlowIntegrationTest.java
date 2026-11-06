package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.dto.response.PaymentUrlResponse;
import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.entity.enums.PaymentStatus;
import com.sportbooking.integration.vnpay.VnPayGateway;
import com.sportbooking.integration.vnpay.VnPayIpnResponse;
import com.sportbooking.integration.vnpay.VnPayProperties;
import com.sportbooking.integration.vnpay.VnPaySigner;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.BookingSlotRepository;
import com.sportbooking.repository.PaymentRepository;
import com.sportbooking.scheduler.BookingExpirationJob;
import com.sportbooking.support.ConcurrencyRunner;
import com.sportbooking.support.ConcurrencyRunner.Outcome;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Luồng thanh toán VNPay với database thật. Test đóng vai VNPay: tự tạo IPN và ký bằng
 * đúng hashSecret của cấu hình test (application-test.yml).
 */
class PaymentFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private VnPayIpnService vnPayIpnService;
    @Autowired
    private BookingExpirationService bookingExpirationService;
    @Autowired
    private BookingExpirationJob bookingExpirationJob;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingSlotRepository bookingSlotRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private VnPayProperties vnPayProperties;
    @Autowired
    private TestDataFactory data;
    @Autowired
    private JdbcTemplate jdbc;

    // ===================== Hỗ trợ =====================

    private record PendingPayment(User customer, BookingResponse booking, PaymentUrlResponse payment) {
    }

    /** Khách mới đặt 1 lượt và lấy link thanh toán. */
    private PendingPayment bookAndStartPayment(TestCourt court, LocalTime start) {
        User customer = data.customer();
        LocalDate date = LocalDate.now(bookingProperties.zone()).plusDays(1);
        BookingResponse booking = bookingService.createBooking(customer.getId(),
                new CreateBookingRequest(court.courtId(), date, start, start.plusHours(1)));
        PaymentUrlResponse payment = paymentService.createVnPayPayment(customer.getId(), booking.id(), "127.0.0.1");
        return new PendingPayment(customer, booking, payment);
    }

    /** Giả lập IPN mà VNPay gửi về, có chữ ký hợp lệ. */
    private Map<String, String> signedIpn(String txnRef, long amountVnd, String responseCode, String transactionStatus) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", vnPayProperties.tmnCode());
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", String.valueOf(amountVnd * 100));
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TransactionNo", "14000001");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_BankTranNo", "VNP14000001");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan dat san");
        params.put("vnp_PayDate", VnPayGateway.VNPAY_TIME.format(LocalDateTime.now(bookingProperties.zone())));
        params.put(VnPaySigner.SECURE_HASH, VnPaySigner.sign(params, vnPayProperties.hashSecret()));
        return params;
    }

    private Map<String, String> successIpn(PendingPayment p) {
        return signedIpn(p.payment().txnRef(), p.payment().amount(), "00", "00");
    }

    private BookingStatus bookingStatus(PendingPayment p) {
        return bookingRepository.findById(p.booking().id()).orElseThrow().getStatus();
    }

    private PaymentStatus paymentStatus(PendingPayment p) {
        return paymentRepository.findByTxnRef(p.payment().txnRef()).orElseThrow().getStatus();
    }

    private void makeOverdue(PendingPayment p) {
        jdbc.update("UPDATE bookings SET expires_at = now() - interval '1 minute' WHERE id = ?", p.booking().id());
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    // ===================== Tạo link thanh toán =====================

    @Test
    void createPayment_returnsSignedVnPayUrl_withAmountTimes100_andPaymentPending() {
        PendingPayment p = bookAndStartPayment(data.footballCourt(), LocalTime.of(17, 0));

        Map<String, String> urlParams = queryParams(p.payment().paymentUrl());

        assertThat(p.payment().paymentUrl()).startsWith(vnPayProperties.payUrl() + "?");
        assertThat(urlParams.get("vnp_TxnRef")).isEqualTo(p.payment().txnRef()).matches("[A-Z0-9]+");
        assertThat(urlParams.get("vnp_Amount")).isEqualTo(String.valueOf(p.booking().totalPrice() * 100));
        assertThat(urlParams.get("vnp_Command")).isEqualTo("pay");
        assertThat(VnPaySigner.verify(urlParams, vnPayProperties.hashSecret())).isTrue();
        assertThat(paymentStatus(p)).isEqualTo(PaymentStatus.PENDING);
    }

    // ===================== IPN =====================

    @Test
    void successfulIpn_confirmsBooking_andTheSameIpnAgainIsIgnored() {
        PendingPayment p = bookAndStartPayment(data.footballCourt(), LocalTime.of(17, 0));
        Map<String, String> ipn = successIpn(p);

        assertThat(vnPayIpnService.handleIpn(ipn)).isEqualTo(VnPayIpnResponse.CONFIRM_SUCCESS);
        assertThat(bookingStatus(p)).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentStatus(p)).isEqualTo(PaymentStatus.SUCCESS);

        // VNPay gửi lại đúng IPN đó (retry): không xử lý lần 2
        assertThat(vnPayIpnService.handleIpn(ipn)).isEqualTo(VnPayIpnResponse.ALREADY_CONFIRMED);
        assertThat(paymentStatus(p)).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void failedIpn_marksPaymentFailed_bookingStaysPending_andCustomerCanRetry() {
        PendingPayment p = bookAndStartPayment(data.footballCourt(), LocalTime.of(17, 0));

        // 24 = khách hủy giao dịch trên trang VNPay
        assertThat(vnPayIpnService.handleIpn(signedIpn(p.payment().txnRef(), p.payment().amount(), "24", "02")))
                .isEqualTo(VnPayIpnResponse.CONFIRM_SUCCESS);
        assertThat(paymentStatus(p)).isEqualTo(PaymentStatus.FAILED);
        assertThat(bookingStatus(p)).isEqualTo(BookingStatus.PENDING);

        PaymentUrlResponse retry = paymentService.createVnPayPayment(p.customer().getId(), p.booking().id(), "127.0.0.1");
        assertThat(retry.txnRef()).isNotEqualTo(p.payment().txnRef());
    }

    @Test
    void invalidIpns_areRejectedWithVnPayErrorCodes() {
        PendingPayment p = bookAndStartPayment(data.footballCourt(), LocalTime.of(17, 0));

        Map<String, String> forged = successIpn(p);
        forged.put("vnp_Amount", "100");   // sửa sau khi ký -> chữ ký sai
        assertThat(vnPayIpnService.handleIpn(forged)).isEqualTo(VnPayIpnResponse.INVALID_SIGNATURE);

        assertThat(vnPayIpnService.handleIpn(signedIpn("KHONGTONTAI123", 1000, "00", "00")))
                .isEqualTo(VnPayIpnResponse.ORDER_NOT_FOUND);

        assertThat(vnPayIpnService.handleIpn(signedIpn(p.payment().txnRef(), p.payment().amount() - 1000, "00", "00")))
                .isEqualTo(VnPayIpnResponse.INVALID_AMOUNT);

        // Không IPN nào ở trên được làm thay đổi dữ liệu
        assertThat(paymentStatus(p)).isEqualTo(PaymentStatus.PENDING);
        assertThat(bookingStatus(p)).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void moneyArrivingAfterBookingExpired_isMarkedRefundRequired() {
        PendingPayment p = bookAndStartPayment(data.footballCourt(), LocalTime.of(17, 0));
        makeOverdue(p);
        bookingExpirationJob.run();
        assertThat(bookingStatus(p)).isEqualTo(BookingStatus.EXPIRED);

        assertThat(vnPayIpnService.handleIpn(successIpn(p))).isEqualTo(VnPayIpnResponse.CONFIRM_SUCCESS);

        assertThat(paymentStatus(p)).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        assertThat(bookingStatus(p)).isEqualTo(BookingStatus.EXPIRED);   // không "hồi sinh" lượt đặt
    }

    // ===================== Đồng thời =====================

    @Test
    void sameIpnDeliveredTenTimesAtOnce_isProcessedExactlyOnce() throws InterruptedException {
        PendingPayment p = bookAndStartPayment(data.footballCourt(), LocalTime.of(17, 0));
        Map<String, String> ipn = successIpn(p);

        List<Outcome<VnPayIpnResponse>> outcomes = ConcurrencyRunner.run(10, i -> () -> vnPayIpnService.handleIpn(ipn));

        assertThat(outcomes).allMatch(Outcome::isSuccess);
        assertThat(outcomes).filteredOn(o -> o.value().equals(VnPayIpnResponse.CONFIRM_SUCCESS)).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.value().equals(VnPayIpnResponse.ALREADY_CONFIRMED)).hasSize(9);
        assertThat(bookingStatus(p)).isEqualTo(BookingStatus.CONFIRMED);
    }

    /**
     * ⭐ IPN báo "đã trả tiền" tới ĐÚNG LÚC job hết hạn đang xử lý booking đó.
     * Dù bên nào thắng, kết quả cuối phải nhất quán, không bao giờ "mất tiền mà không có sân"
     * hay "có sân mà slot đã bị nhả".
     */
    @Test
    void ipnRacingWithExpirationJob_alwaysEndsInAConsistentState() throws InterruptedException {
        TestCourt court = data.footballCourt();
        int confirmed = 0;
        int refunds = 0;

        for (int round = 0; round < 5; round++) {
            PendingPayment p = bookAndStartPayment(court, LocalTime.of(7, 0).plusHours(round));
            makeOverdue(p);
            Map<String, String> ipn = successIpn(p);

            List<Outcome<Object>> outcomes = ConcurrencyRunner.<Object>run(2, i -> i == 0
                    ? () -> bookingExpirationService.expireIfOverdue(p.booking().id())
                    : () -> vnPayIpnService.handleIpn(ipn));

            // IPN thua cuộc đua trả 99 -> VNPay sẽ gửi lại. Mô phỏng lần gửi lại đó.
            if (VnPayIpnResponse.UNKNOWN_ERROR.equals(outcomes.get(1).value())) {
                assertThat(vnPayIpnService.handleIpn(ipn)).isEqualTo(VnPayIpnResponse.CONFIRM_SUCCESS);
            }

            BookingStatus booking = bookingStatus(p);
            PaymentStatus payment = paymentStatus(p);
            long slots = bookingSlotRepository.countByBookingId(p.booking().id());
            if (booking == BookingStatus.CONFIRMED) {
                assertThat(payment).isEqualTo(PaymentStatus.SUCCESS);
                assertThat(slots).isEqualTo(2);
                confirmed++;
            } else {
                assertThat(booking).isEqualTo(BookingStatus.EXPIRED);
                assertThat(payment).isEqualTo(PaymentStatus.REFUND_REQUIRED);
                assertThat(slots).isZero();
                refunds++;
            }
        }
        System.out.printf("[RACE] IPN thắng (xác nhận sân) %d lần, job thắng (cần hoàn tiền) %d lần%n", confirmed, refunds);
    }
}
