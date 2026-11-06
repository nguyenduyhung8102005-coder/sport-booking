package com.sportbooking.entity.enums;

/**
 * PENDING   : vừa đặt, đang giữ chỗ chờ thanh toán (có hạn expires_at)
 * CONFIRMED : đã thanh toán (GĐ 6)
 * CANCELLED : khách/chủ sân hủy (GĐ 5)
 * EXPIRED   : quá hạn thanh toán, hệ thống tự hủy (GĐ 5)
 * COMPLETED : đã chơi xong (GĐ 7, điều kiện để đánh giá)
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    COMPLETED
}
