package com.sportbooking.dto.request;

import jakarta.validation.constraints.Size;

/** Khách hủy: lý do không bắt buộc. */
public record CancelBookingRequest(
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
