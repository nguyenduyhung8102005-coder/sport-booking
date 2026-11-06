package com.sportbooking.scheduler;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.service.BookingExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Job chạy nền: chuyển các booking PENDING quá hạn thanh toán sang EXPIRED và nhả slot.
 *
 * fixedDelay (không phải fixedRate): lần chạy sau bắt đầu 30s SAU KHI lần trước KẾT THÚC.
 * Lần chạy nào lâu (nhiều booking) cũng không bị chồng lên lần kế tiếp.
 *
 * Chạy nhiều instance app: các instance có thể cùng xử lý 1 booking. Vẫn ĐÚNG nhờ @Version
 * (chỉ 1 instance thắng, instance kia gặp optimistic lock và bỏ qua), chỉ tốn công vô ích.
 * Cách tối ưu (sau MVP): ShedLock hoặc khóa phân tán bằng Redis để chỉ 1 instance chạy job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationJob {

    private final BookingExpirationService bookingExpirationService;
    private final BookingProperties bookingProperties;

    @Scheduled(initialDelayString = "${app.booking.expiration-check-interval-ms}",
            fixedDelayString = "${app.booking.expiration-check-interval-ms}")
    public void scheduledRun() {
        run();
    }

    /** Tách khỏi method @Scheduled để test gọi trực tiếp và kiểm tra kết quả. */
    public int run() {
        List<Long> overdueIds = bookingExpirationService.findOverdueBookingIds(bookingProperties.expirationBatchSize());
        int expired = 0;
        for (Long bookingId : overdueIds) {
            try {
                if (bookingExpirationService.expireIfOverdue(bookingId)) {
                    expired++;
                }
            } catch (OptimisticLockingFailureException e) {
                // Khách vừa hủy / thanh toán đúng lúc này: họ thắng, job bỏ qua. Đây là tình huống bình thường.
                log.info("Booking {} was changed concurrently, skip expiring", bookingId);
            } catch (RuntimeException e) {
                // Không để 1 booking lỗi làm dừng cả job; lần chạy sau sẽ thử lại.
                log.error("Failed to expire booking {}", bookingId, e);
            }
        }
        if (expired > 0) {
            log.info("Expired {} overdue booking(s)", expired);
        }
        return expired;
    }
}
