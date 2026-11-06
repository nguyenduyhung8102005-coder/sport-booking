package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.entity.enums.SportType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Một dòng trong lịch đặt của chủ sân: có thông tin liên hệ của khách. */
public record OwnerBookingResponse(
        Long id,
        String bookingCode,
        Long courtId,
        String courtName,
        SportType sportType,
        String customerName,
        String customerPhone,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        long totalPrice,
        BookingStatus status,
        String cancelReason,
        Instant createdAt) {
}
