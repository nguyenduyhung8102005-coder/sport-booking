package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.VenueStatus;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/** Chi tiết cụm sân kèm danh sách sân con và bảng giá của từng sân. */
public record VenueDetailResponse(
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
        List<CourtResponse> courts) {
}
