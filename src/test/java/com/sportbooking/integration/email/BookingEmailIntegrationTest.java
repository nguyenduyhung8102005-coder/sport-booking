package com.sportbooking.integration.email;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.dto.response.PaymentUrlResponse;
import com.sportbooking.entity.User;
import com.sportbooking.event.BookingConfirmedEvent;
import com.sportbooking.event.BookingEmailData;
import com.sportbooking.integration.vnpay.VnPayIpnResponse;
import com.sportbooking.integration.vnpay.VnPayProperties;
import com.sportbooking.integration.vnpay.VnPaySigner;
import com.sportbooking.service.BookingService;
import com.sportbooking.service.OwnerBookingService;
import com.sportbooking.service.PaymentService;
import com.sportbooking.service.VnPayIpnService;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Email được gửi ĐÚNG LÚC: sau khi transaction commit, trên thread khác, và KHÔNG gửi nếu rollback.
 *
 * @MockitoBean thay JavaMailSender thật bằng bản giả: không cần máy chủ SMTP, và kiểm tra được
 * "đã gửi gì, cho ai". Vì gửi bất đồng bộ, verify dùng timeout(...) để chờ tối đa vài giây.
 */
@TestPropertySource(properties = "app.mail.enabled=true")
class BookingEmailIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private VnPayIpnService vnPayIpnService;
    @Autowired
    private OwnerBookingService ownerBookingService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private VnPayProperties vnPayProperties;
    @Autowired
    private TestDataFactory data;

    private BookingResponse book(TestCourt court, User customer) {
        return bookingService.createBooking(customer.getId(), new CreateBookingRequest(court.courtId(),
                LocalDate.now(bookingProperties.zone()).plusDays(1), LocalTime.of(17, 0), LocalTime.of(18, 0)));
    }

    @Test
    void successfulPayment_sendsConfirmationEmailToCustomer_afterCommit() {
        User customer = data.customer();
        BookingResponse booking = book(data.footballCourt(), customer);
        PaymentUrlResponse payment = paymentService.createVnPayPayment(customer.getId(), booking.id(), "127.0.0.1");

        Map<String, String> ipn = new HashMap<>();
        ipn.put("vnp_TmnCode", vnPayProperties.tmnCode());
        ipn.put("vnp_TxnRef", payment.txnRef());
        ipn.put("vnp_Amount", String.valueOf(payment.amount() * 100));
        ipn.put("vnp_ResponseCode", "00");
        ipn.put("vnp_TransactionStatus", "00");
        ipn.put("vnp_TransactionNo", "14000002");
        ipn.put(VnPaySigner.SECURE_HASH, VnPaySigner.sign(ipn, vnPayProperties.hashSecret()));
        assertThat(vnPayIpnService.handleIpn(ipn)).isEqualTo(VnPayIpnResponse.CONFIRM_SUCCESS);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(customer.getEmail());
        assertThat(sent.getValue().getSubject()).contains(booking.bookingCode());
    }

    @Test
    void eventPublishedInRolledBackTransaction_sendsNoEmail() {
        BookingEmailData data = new BookingEmailData("BKROLLBACK0001", "rollback@test.vn", "Test", "V", "A", "S",
                LocalDate.now(), LocalTime.of(17, 0), LocalTime.of(18, 0), 300_000);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new BookingConfirmedEvent(data));
            status.setRollbackOnly();   // giả lập: xác nhận thất bại, transaction rollback
        });

        // Đợi 1,5 giây để chắc chắn: không có email nào nhắc tới mã này được gửi đi
        verify(mailSender, after(1500).never())
                .send(argThat((SimpleMailMessage message) -> message.getSubject().contains("BKROLLBACK0001")));
    }

    @Test
    void ownerCancellation_sendsEmailWithReason() {
        TestCourt court = data.footballCourt();
        User customer = data.customer();
        BookingResponse booking = book(court, customer);

        ownerBookingService.cancelByOwner(court.ownerId(), booking.id(), "Sân bảo trì đột xuất");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(customer.getEmail());
        assertThat(sent.getValue().getText()).contains("Sân bảo trì đột xuất");
    }
}
