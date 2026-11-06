package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.dto.request.OwnerApplicationRequest;
import com.sportbooking.dto.response.OwnerApplicationResponse;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.OwnerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Owner application", description = "Khách hàng đăng ký trở thành chủ sân")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner-applications")
@RequiredArgsConstructor
public class OwnerApplicationController {

    private final OwnerApplicationService ownerApplicationService;

    @Operation(summary = "Gửi (hoặc gửi lại sau khi bị từ chối) yêu cầu làm chủ sân")
    @PostMapping
    public ResponseEntity<ApiResponse<OwnerApplicationResponse>> apply(@AuthenticationPrincipal UserPrincipal principal,
                                                                       @Valid @RequestBody OwnerApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ownerApplicationService.apply(principal.id(), request)));
    }

    @Operation(summary = "Xem trạng thái yêu cầu của tôi")
    @GetMapping("/me")
    public ApiResponse<OwnerApplicationResponse> getMine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ownerApplicationService.getMyApplication(principal.id()));
    }
}
