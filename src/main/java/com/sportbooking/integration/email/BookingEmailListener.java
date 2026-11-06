package com.sportbooking.integration.email;

import com.sportbooking.config.AsyncConfig;
import com.sportbooking.event.BookingCancelledByOwnerEvent;
import com.sportbooking.event.BookingConfirmedEvent;
import com.sportbooking.integration.email.BookingEmailComposer.EmailContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gửi email khi có sự kiện về lượt đặt.
 *
 * @TransactionalEventListener(AFTER_COMMIT): chỉ chạy SAU KHI transaction phát sự kiện commit thành công.
 *   Nếu dùng @EventListener thường (hoặc gọi mailSender trực tiếp trong service), email được gửi NGAY,
 *   rồi transaction có thể ROLLBACK (vd thua optimistic lock) -> khách nhận email "đã xác nhận"
 *   cho lượt đặt thực ra chưa được xác nhận. Email đã gửi thì không thu hồi được.
 *
 * @Async: chạy trên thread pool "mailExecutor". Request IPN / hủy sân trả lời ngay,
 *   không phải chờ máy chủ SMTP.
 *
 * Đánh đổi: app sập đúng lúc giữa commit và gửi -> mất email. Muốn chắc chắn gửi được: Outbox pattern
 * (ghi "việc cần gửi" vào bảng trong CÙNG transaction, job đọc bảng đó để gửi) - phần mở rộng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEmailListener {

    private final JavaMailSender mailSender;
    private final NotificationMailProperties mailProperties;

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        send(event.data().customerEmail(), BookingEmailComposer.confirmed(event.data()));
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelledByOwner(BookingCancelledByOwnerEvent event) {
        send(event.data().customerEmail(), BookingEmailComposer.cancelledByOwner(event.data(), event.reason()));
    }

    private void send(String to, EmailContent content) {
        if (!mailProperties.enabled()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.from());
        message.setTo(to);
        message.setSubject(content.subject());
        message.setText(content.body());
        try {
            mailSender.send(message);
            log.info("Sent email '{}' to {}", content.subject(), to);
        } catch (MailException e) {
            // Không ném tiếp: lỗi email không ảnh hưởng nghiệp vụ đã hoàn tất
            log.error("Failed to send email '{}' to {}", content.subject(), to, e);
        }
    }
}
