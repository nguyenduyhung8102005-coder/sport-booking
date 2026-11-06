package com.sportbooking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Chủ sân hủy: BẮT BUỘC ghi lý do để khách biết (sân hỏng, mất điện...). */
public record OwnerCancelBookingRequest(
        @NotBlank(message = "Lý do hủy không được để trống")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
