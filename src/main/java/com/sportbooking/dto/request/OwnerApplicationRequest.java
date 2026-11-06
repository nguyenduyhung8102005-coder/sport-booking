package com.sportbooking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerApplicationRequest(
        @NotBlank(message = "Tên cơ sở kinh doanh không được để trống")
        @Size(max = 150, message = "Tên cơ sở kinh doanh tối đa 150 ký tự")
        String businessName) {
}
