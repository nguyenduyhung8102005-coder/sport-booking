package com.sportbooking.dto.request;

import com.sportbooking.entity.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        UserStatus status) {
}
