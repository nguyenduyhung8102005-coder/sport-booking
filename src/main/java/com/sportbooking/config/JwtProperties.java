package com.sportbooking.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Gom cấu hình app.jwt.* thành 1 object có kiểu rõ ràng (thay vì rải @Value khắp nơi).
 * @Validated: thiếu cấu hình thì app báo lỗi ngay lúc khởi động.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}
