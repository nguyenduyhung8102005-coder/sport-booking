package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.ApprovalStatus;

import java.time.Instant;

public record OwnerApplicationResponse(
        Long userId,
        String email,
        String fullName,
        String businessName,
        ApprovalStatus status,
        Instant createdAt,
        Instant reviewedAt,
        String rejectReason) {
}
