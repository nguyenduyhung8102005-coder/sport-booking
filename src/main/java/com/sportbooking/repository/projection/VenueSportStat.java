package com.sportbooking.repository.projection;

import com.sportbooking.entity.enums.SportType;

/**
 * Kết quả của câu query thống kê (GROUP BY), không phải entity.
 * JPQL "SELECT new ...VenueSportStat(...)" tạo trực tiếp object này từ từng dòng kết quả.
 */
public record VenueSportStat(
        Long venueId,
        SportType sportType,
        Long courtCount,
        Long minPricePerHour,
        Long maxPricePerHour) {
}
