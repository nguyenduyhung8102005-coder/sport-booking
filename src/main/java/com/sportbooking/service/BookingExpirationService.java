package com.sportbooking.service;

import com.sportbooking.entity.Booking;
import com.sportbooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Logic hết hạn giữ chỗ. Vòng lặp gọi các method này nằm ở BookingExpirationJob (bean KHÁC),
 * để mỗi lần gọi expireIfOverdue đi qua proxy của Spring và có transaction RIÊNG.
 *
 * Nếu đặt vòng lặp ngay trong class này và gọi this.expireIfOverdue(id), lời gọi nội bộ
 * KHÔNG đi qua proxy => @Transactional bị bỏ qua (lỗi self-invocation rất hay gặp khi phỏng vấn).
 */
@Service
@RequiredArgsConstructor
public class BookingExpirationService {

    private final BookingRepository bookingRepository;
    private final BookingSlotReleaser bookingSlotReleaser;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> findOverdueBookingIds(int limit) {
        return bookingRepository.findOverdueIds(clock.instant(), PageRequest.of(0, limit));
    }

    /**
     * Mỗi booking một transaction riêng: 1 booking lỗi không làm hỏng cả lô.
     *
     * Kiểm tra lại isOverdue SAU KHI load: giữa lúc lấy danh sách id và lúc xử lý,
     * khách có thể vừa hủy (hoặc, ở GĐ 6, vừa thanh toán xong) -> bỏ qua.
     *
     * Nếu khách hủy / thanh toán ĐÚNG LÚC job đang xử lý: cả 2 cùng đọc thấy PENDING (version = 5),
     * ai UPDATE trước thì thắng (version -> 6); người sau UPDATE "... WHERE version = 5" được 0 dòng
     * -> Hibernate ném OptimisticLockException -> transaction của người sau rollback.
     * Exception được ném ra ngoài (không bắt ở đây) để job ghi log và bỏ qua booking đó.
     *
     * @return true nếu booking vừa được chuyển sang EXPIRED
     */
    @Transactional
    public boolean expireIfOverdue(Long bookingId) {
        Instant now = clock.instant();
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || !booking.isOverdue(now)) {
            return false;
        }
        booking.expire();
        bookingSlotReleaser.releaseSlots(booking);
        return true;
    }
}
