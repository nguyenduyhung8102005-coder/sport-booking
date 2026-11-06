package com.sportbooking.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cấu hình Swagger UI (http://localhost:8080/swagger-ui.html).
 *
 * SecurityScheme "bearerAuth": nút Authorize để dán access token; endpoint có @SecurityRequirement("bearerAuth")
 * sẽ tự gắn header Authorization khi bấm "Try it out".
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Sport Booking API",
        version = "v1",
        description = """
                Hệ thống đặt sân bóng đá mini và cầu lông.

                **Cách thử nhanh:** gọi `POST /api/v1/auth/login` với tài khoản demo
                (`customer@sportbooking.vn` / `Demo@123456`, profile dev), sao chép `accessToken`, bấm **Authorize** và dán vào.

                Mọi response có dạng `{ success, data | error, timestamp }`. Lỗi có `error.code` cố định
                (vd `SLOT_ALREADY_BOOKED`) để client xử lý, `error.message` để hiển thị.
                """))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    /**
     * Thêm các response lỗi chung (400/401/403) kèm ví dụ JSON vào tài liệu của mọi endpoint,
     * thay vì lặp lại @ApiResponse trên từng method. Endpoint nào đã tự khai báo mã đó thì giữ nguyên.
     */
    @Bean
    public OpenApiCustomizer commonErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            ApiResponses responses = operation.getResponses();
            if (acceptsInput(operation)) {
                responses.putIfAbsent("400", error("Dữ liệu không hợp lệ", "VALIDATION_FAILED", "Dữ liệu không hợp lệ"));
            }
            if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
                responses.putIfAbsent("401", error("Chưa đăng nhập, token sai hoặc hết hạn",
                        "TOKEN_EXPIRED", "Access token đã hết hạn"));
                responses.putIfAbsent("403", error("Không đủ quyền hoặc tài khoản bị khóa",
                        "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này"));
            }
        }));
    }

    private static boolean acceptsInput(Operation operation) {
        return operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    }

    private static ApiResponse error(String description, String code, String message) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", code);
        errorBody.put("message", message);
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("success", false);
        example.put("error", errorBody);
        example.put("timestamp", "2026-11-06T10:15:30Z");
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().example(example)));
    }
}
