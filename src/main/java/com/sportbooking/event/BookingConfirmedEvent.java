package com.sportbooking.event;

/** Lượt đặt vừa được thanh toán và xác nhận (phát ra trong VnPayIpnService). */
public record BookingConfirmedEvent(BookingEmailData data) {
}
