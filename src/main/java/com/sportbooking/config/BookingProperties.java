package com.sportbooking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

/** Các quy tắc đặt sân, đọc từ app.booking.* để đổi được mà không sửa code. */
@Validated
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        @NotNull ZoneId zone,
        @NotNull Duration holdDuration,
        @Min(1) int maxAdvanceDays,
        @Min(1) int maxSlotsPerBooking,
        @Min(1) int maxPendingPerCustomer) {
}
