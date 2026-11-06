package com.sportbooking.event;

/** Chủ sân vừa hủy lượt đặt của khách (phát ra trong OwnerBookingService). */
public record BookingCancelledByOwnerEvent(BookingEmailData data, String reason) {
}
