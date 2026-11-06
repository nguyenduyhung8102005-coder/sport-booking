package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.entity.enums.SportType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingResponse(
        Long id,
        String bookingCode,
        Long venueId,
        String venueName,
        String venueAddress,
        Long courtId,
        String courtName,
        SportType sportType,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        long totalPrice,
        BookingStatus status,
        Instant expiresAt,      // hạn thanh toán khi PENDING
        String cancelReason,    // có giá trị khi CANCELLED
        Instant createdAt) {
}
