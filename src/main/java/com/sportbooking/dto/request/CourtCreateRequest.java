package com.sportbooking.dto.request;

import com.sportbooking.entity.enums.SportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourtCreateRequest(
        @NotBlank(message = "Tên sân không được để trống")
        @Size(max = 50, message = "Tên sân tối đa 50 ký tự")
        String name,

        @NotNull(message = "Loại sân không được để trống")
        SportType sportType) {
}
