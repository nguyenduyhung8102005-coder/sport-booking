package com.sportbooking.repository.criteria;

import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.SportType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Điều kiện mà ÍT NHẤT MỘT sân con của cụm sân phải thỏa (tất cả cùng lúc).
 * Gom thành record thay vì truyền 7 tham số rời rạc dễ nhầm thứ tự.
 */
public record CourtCriteria(
        SportType sportType,
        DayType dayType,
        Long minPrice,
        Long maxPrice,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime) {

    public boolean hasPriceFilter() {
        return minPrice != null || maxPrice != null;
    }

    public boolean hasTimeRange() {
        return date != null && startTime != null && endTime != null;
    }

    public boolean isEmpty() {
        return sportType == null && !hasPriceFilter() && !hasTimeRange();
    }
}
