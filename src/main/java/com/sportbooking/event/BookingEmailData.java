package com.sportbooking.event;

import com.sportbooking.entity.Booking;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * "Ảnh chụp" dữ liệu cần cho email, tạo NGAY TRONG transaction lúc booking còn được quản lý.
 *
 * Vì sao không chỉ gửi bookingId rồi để luồng gửi mail tự đọc DB?
 *  - Luồng @Async chạy ở thread khác, không có transaction -> đọc quan hệ LAZY sẽ lỗi.
 *  - Email phản ánh đúng dữ liệu tại thời điểm sự kiện xảy ra.
 * Đánh đổi: event to hơn một chút.
 */
public record BookingEmailData(
        String bookingCode,
        String customerEmail,
        String customerName,
        String venueName,
        String venueAddress,
        String courtName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        long totalPrice) {

    /** Phải gọi trong transaction (customer, court, venue là LAZY). */
    public static BookingEmailData from(Booking booking) {
        return new BookingEmailData(
                booking.getBookingCode(),
                booking.getCustomer().getEmail(),
                booking.getCustomer().getFullName(),
                booking.getCourt().getVenue().getName(),
                booking.getCourt().getVenue().getAddress(),
                booking.getCourt().getName(),
                booking.getBookingDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getTotalPrice());
    }
}
