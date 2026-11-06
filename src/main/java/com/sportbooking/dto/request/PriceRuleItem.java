package com.sportbooking.dto.request;

import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.PriceLabel;
import com.sportbooking.validation.HalfHourTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

public record PriceRuleItem(
        @NotNull(message = "Loại ngày không được để trống")
        DayType dayType,

        @Schema(type = "string", example = "17:00")
        @NotNull(message = "Giờ bắt đầu không được để trống")
        @HalfHourTime
        LocalTime startTime,

        @Schema(type = "string", example = "22:00")
        @NotNull(message = "Giờ kết thúc không được để trống")
        @HalfHourTime
        LocalTime endTime,

        @Schema(example = "500000")
        @NotNull(message = "Giá không được để trống")
        @Positive(message = "Giá phải lớn hơn 0")
        @Max(value = 10_000_000, message = "Giá tối đa 10.000.000đ/giờ")
        Long pricePerHour,

        // Không bắt buộc, mặc định NORMAL
        PriceLabel label) {
}
