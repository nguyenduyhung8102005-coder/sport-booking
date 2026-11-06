package com.sportbooking.scheduler;

import com.sportbooking.service.BookingCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mỗi 5 phút: chuyển các lượt đã thanh toán và đã chơi xong sang COMPLETED.
 * Khác BookingExpirationJob: chỉ 1 câu UPDATE hàng loạt, không cần xử lý từng booking,
 * vì không có slot nào phải nhả và không có bên nào khác hay tranh sửa lượt đặt đã qua giờ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCompletionJob {

    private final BookingCompletionService bookingCompletionService;

    @Scheduled(initialDelayString = "${app.booking.completion-check-interval-ms}",
            fixedDelayString = "${app.booking.completion-check-interval-ms}")
    public void scheduledRun() {
        int completed = bookingCompletionService.completeFinishedBookings();
        if (completed > 0) {
            log.info("Marked {} booking(s) as COMPLETED", completed);
        }
    }
}
