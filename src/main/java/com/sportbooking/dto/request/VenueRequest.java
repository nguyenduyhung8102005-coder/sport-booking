package com.sportbooking.dto.request;

import com.sportbooking.validation.HalfHourTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** Dùng chung cho tạo mới và cập nhật cụm sân. */
public record VenueRequest(
        @NotBlank(message = "Tên cụm sân không được để trống")
        @Size(max = 150, message = "Tên cụm sân tối đa 150 ký tự")
        String name,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        @NotBlank(message = "Địa chỉ không được để trống")
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        @NotBlank(message = "Quận/huyện không được để trống")
        @Size(max = 100, message = "Quận/huyện tối đa 100 ký tự")
        String district,

        @NotBlank(message = "Tỉnh/thành phố không được để trống")
        @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự")
        String city,

        @Schema(type = "string", example = "06:00")
        @NotNull(message = "Giờ mở cửa không được để trống")
        @HalfHourTime
        LocalTime openTime,

        @Schema(type = "string", example = "23:00")
        @NotNull(message = "Giờ đóng cửa không được để trống")
        @HalfHourTime
        LocalTime closeTime) {
}
