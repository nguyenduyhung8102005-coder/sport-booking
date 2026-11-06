package com.sportbooking.dto.request;

import com.sportbooking.validation.HalfHourTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Không có customerId (lấy từ token) và không có giá (server tự tính từ bảng giá).
 * Không bao giờ tin giá do client gửi lên.
 */
public record CreateBookingRequest(
        @NotNull(message = "Sân không được để trống")
        Long courtId,

        @Schema(type = "string", example = "2026-11-07")
        @NotNull(message = "Ngày đặt không được để trống")
        LocalDate date,

        @Schema(type = "string", example = "17:00")
        @NotNull(message = "Giờ bắt đầu không được để trống")
        @HalfHourTime
        LocalTime startTime,

        @Schema(type = "string", example = "18:30")
        @NotNull(message = "Giờ kết thúc không được để trống")
        @HalfHourTime
        LocalTime endTime) {
}
