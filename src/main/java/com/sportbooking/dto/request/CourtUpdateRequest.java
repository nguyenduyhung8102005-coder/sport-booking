package com.sportbooking.dto.request;

import com.sportbooking.entity.enums.CourtStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Không có sportType: loại sân không được đổi sau khi tạo. */
public record CourtUpdateRequest(
        @NotBlank(message = "Tên sân không được để trống")
        @Size(max = 50, message = "Tên sân tối đa 50 ký tự")
        String name,

        @NotNull(message = "Trạng thái không được để trống")
        CourtStatus status) {
}
