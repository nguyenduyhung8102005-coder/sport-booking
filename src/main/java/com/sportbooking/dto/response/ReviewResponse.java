package com.sportbooking.dto.response;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        int rating,
        String comment,
        String customerName,
        Instant createdAt) {
}
