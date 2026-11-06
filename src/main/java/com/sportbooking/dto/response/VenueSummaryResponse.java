package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.SportType;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/** Một dòng trong kết quả tìm kiếm sân (public). */
public record VenueSummaryResponse(
        Long id,
        String name,
        String address,
        String district,
        String city,
        LocalTime openTime,
        LocalTime closeTime,
        BigDecimal avgRating,
        int reviewCount,
        List<SportSummary> sports) {

    /** "3 sân 5 người, giá 300.000đ – 550.000đ/giờ". Giá null nếu chưa cấu hình bảng giá. */
    public record SportSummary(
            SportType sportType,
            long courtCount,
            Long minPricePerHour,
            Long maxPricePerHour) {
    }
}
