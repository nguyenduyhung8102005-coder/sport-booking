package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.Role;
import com.sportbooking.entity.enums.UserStatus;

import java.time.Instant;

/** Không có passwordHash: DTO quyết định chính xác field nào được lộ ra ngoài. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        UserStatus status,
        Instant createdAt) {
}
