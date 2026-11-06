package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.common.PageResponse;
import com.sportbooking.dto.request.RejectOwnerApplicationRequest;
import com.sportbooking.dto.request.UpdateUserStatusRequest;
import com.sportbooking.dto.response.OwnerApplicationResponse;
import com.sportbooking.dto.response.UserResponse;
import com.sportbooking.entity.enums.ApprovalStatus;
import com.sportbooking.entity.enums.Role;
import com.sportbooking.entity.enums.UserStatus;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.OwnerApplicationService;
import com.sportbooking.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Toàn bộ /api/v1/admin/** đã được SecurityConfig giới hạn cho role ADMIN.
 */
@Tag(name = "Admin", description = "Quản lý người dùng và duyệt chủ sân")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final OwnerApplicationService ownerApplicationService;

    @Operation(summary = "Danh sách người dùng (lọc theo role, trạng thái, từ khóa email/họ tên)")
    @GetMapping("/users")
    public ApiResponse<PageResponse<UserResponse>> searchUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(userService.searchUsers(role, status, keyword, pageable));
    }

    @Operation(summary = "Khóa / mở khóa tài khoản")
    @PatchMapping("/users/{userId}/status")
    public ApiResponse<UserResponse> changeUserStatus(@PathVariable Long userId,
                                                      @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.ok(userService.changeStatus(userId, request.status()));
    }

    @Operation(summary = "Danh sách yêu cầu làm chủ sân")
    @GetMapping("/owner-applications")
    public ApiResponse<PageResponse<OwnerApplicationResponse>> searchOwnerApplications(
            @RequestParam(required = false) ApprovalStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiResponse.ok(ownerApplicationService.search(status, pageable));
    }

    @Operation(summary = "Duyệt yêu cầu làm chủ sân")
    @PostMapping("/owner-applications/{userId}/approve")
    public ApiResponse<OwnerApplicationResponse> approve(@AuthenticationPrincipal UserPrincipal admin,
                                                         @PathVariable Long userId) {
        return ApiResponse.ok(ownerApplicationService.approve(admin.id(), userId));
    }

    @Operation(summary = "Từ chối yêu cầu làm chủ sân")
    @PostMapping("/owner-applications/{userId}/reject")
    public ApiResponse<OwnerApplicationResponse> reject(@AuthenticationPrincipal UserPrincipal admin,
                                                        @PathVariable Long userId,
                                                        @Valid @RequestBody RejectOwnerApplicationRequest request) {
        return ApiResponse.ok(ownerApplicationService.reject(admin.id(), userId, request.reason()));
    }
}
