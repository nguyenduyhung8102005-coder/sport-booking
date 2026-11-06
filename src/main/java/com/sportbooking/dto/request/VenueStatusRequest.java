package com.sportbooking.dto.request;

import com.sportbooking.entity.enums.VenueStatus;
import jakarta.validation.constraints.NotNull;

public record VenueStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        VenueStatus status) {
}
