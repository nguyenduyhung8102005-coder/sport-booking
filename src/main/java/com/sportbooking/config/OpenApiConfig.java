package com.sportbooking.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Swagger. SecurityScheme "bearerAuth" khai báo sẵn cho Giai đoạn 2 (JWT):
 * Swagger sẽ có nút Authorize để dán access token.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Sport Booking API",
        version = "v1",
        description = "Hệ thống đặt sân bóng đá mini và cầu lông"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
