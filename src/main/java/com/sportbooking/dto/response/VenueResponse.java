package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.VenueStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

/** Thông tin cơ bản của cụm sân, dùng cho danh sách sân của chủ sân. */
public record VenueResponse(
        Long id,
        String name,
        String description,
        String address,
        String district,
        String city,
        LocalTime openTime,
        LocalTime closeTime,
        VenueStatus status,
        BigDecimal avgRating,
        int reviewCount,
        Instant createdAt) {
}
