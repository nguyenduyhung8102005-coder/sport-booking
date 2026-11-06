package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller TẠM để kiểm tra response format và exception handler.
 * Sẽ xóa ở Giai đoạn 2.
 */
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    @GetMapping
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @PostMapping("/echo")
    public ApiResponse<EchoRequest> echo(@Valid @RequestBody EchoRequest request) {
        return ApiResponse.ok(request);
    }

    @GetMapping("/not-found")
    public ApiResponse<Void> notFound() {
        throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sân id=99");
    }

    public record EchoRequest(
            @NotBlank(message = "Tên không được để trống")
            String name,

            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không hợp lệ")
            String email,

            @Min(value = 1, message = "Rating tối thiểu là 1")
            @Max(value = 5, message = "Rating tối đa là 5")
            Integer rating) {
    }
}
