package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.dto.request.LoginRequest;
import com.sportbooking.dto.request.RefreshTokenRequest;
import com.sportbooking.dto.request.RegisterRequest;
import com.sportbooking.dto.response.AuthResponse;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller chỉ làm 3 việc: nhận request, validate (@Valid), gọi service.
 * Không chứa logic nghiệp vụ.
 */
@Tag(name = "Auth", description = "Đăng ký, đăng nhập, làm mới token, đăng xuất")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Đăng ký tài khoản khách hàng")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.register(request)));
    }

    @Operation(summary = "Đăng nhập bằng email và mật khẩu")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "Đổi refresh token lấy cặp token mới (refresh token cũ bị thu hồi)")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Đăng xuất: thu hồi refresh token của phiên hiện tại")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(principal.id(), request.refreshToken());
        return ApiResponse.ok(null);
    }
}
