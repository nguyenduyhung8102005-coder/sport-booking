package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.PriceLabel;

import java.time.LocalTime;

public record PriceRuleResponse(
        DayType dayType,
        LocalTime startTime,
        LocalTime endTime,
        long pricePerHour,
        PriceLabel label) {
}
