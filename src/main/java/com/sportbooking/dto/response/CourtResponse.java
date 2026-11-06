package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.CourtStatus;
import com.sportbooking.entity.enums.SportType;

import java.util.List;

public record CourtResponse(
        Long id,
        String name,
        SportType sportType,
        CourtStatus status,
        List<PriceRuleResponse> priceRules) {
}
