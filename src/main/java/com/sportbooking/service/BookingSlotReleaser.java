package com.sportbooking.service;

import com.sportbooking.entity.Booking;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.BookingSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bước chung khi một booking rời trạng thái giữ chỗ (hết hạn, bị hủy): nhả các slot của nó.
 * Mọi luồng (job hết hạn, khách hủy, chủ sân hủy, sau này là thanh toán) đều đi qua đây,
 * nên thứ tự khóa luôn giống nhau.
 */
@Component
@RequiredArgsConstructor
class BookingSlotReleaser {

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;

    /**
     * Gọi SAU khi đã đổi trạng thái booking, trong cùng transaction.
     *
     * 1. flush(): chạy NGAY câu "UPDATE bookings ... WHERE id = ? AND version = ?".
     *    Nếu booking vừa bị luồng khác đổi trạng thái -> lỗi optimistic lock xảy ra tại đây,
     *    TRƯỚC khi đụng tới slot. Transaction rollback, không có gì bị xóa nhầm.
     * 2. Sau đó mới xóa slot.
     *
     * Thứ tự cố định "khóa dòng bookings -> khóa dòng booking_slots" cho mọi luồng => không deadlock.
     * (Nếu luồng A xóa slot trước rồi mới UPDATE booking, còn luồng B làm ngược lại,
     *  A và B có thể chờ nhau mãi mãi.)
     */
    void releaseSlots(Booking booking) {
        bookingRepository.flush();
        bookingSlotRepository.deleteByBookingId(booking.getId());
    }
}
