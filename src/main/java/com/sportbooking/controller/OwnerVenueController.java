package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.common.PageResponse;
import com.sportbooking.dto.request.CourtCreateRequest;
import com.sportbooking.dto.request.VenueRequest;
import com.sportbooking.dto.request.VenueStatusRequest;
import com.sportbooking.dto.response.CourtResponse;
import com.sportbooking.dto.response.VenueDetailResponse;
import com.sportbooking.dto.response.VenueResponse;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.VenueManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/owner/** chỉ dành cho role OWNER (SecurityConfig).
 * ownerId luôn lấy từ token, không bao giờ nhận từ request.
 */
@Tag(name = "Owner - Venue", description = "Chủ sân quản lý cụm sân và sân con của mình")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner/venues")
@RequiredArgsConstructor
public class OwnerVenueController {

    private final VenueManagementService venueManagementService;

    @Operation(summary = "Tạo cụm sân")
    @PostMapping
    public ResponseEntity<ApiResponse<VenueResponse>> create(@AuthenticationPrincipal UserPrincipal owner,
                                                             @Valid @RequestBody VenueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(venueManagementService.createVenue(owner.id(), request)));
    }

    @Operation(summary = "Danh sách cụm sân của tôi")
    @GetMapping
    public ApiResponse<PageResponse<VenueResponse>> listMine(
            @AuthenticationPrincipal UserPrincipal owner,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(venueManagementService.listMyVenues(owner.id(), pageable));
    }

    @Operation(summary = "Chi tiết cụm sân của tôi (gồm cả sân đang bảo trì)")
    @GetMapping("/{venueId}")
    public ApiResponse<VenueDetailResponse> getMine(@AuthenticationPrincipal UserPrincipal owner,
                                                    @PathVariable Long venueId) {
        return ApiResponse.ok(venueManagementService.getMyVenue(owner.id(), venueId));
    }

    @Operation(summary = "Cập nhật thông tin cụm sân")
    @PutMapping("/{venueId}")
    public ApiResponse<VenueResponse> update(@AuthenticationPrincipal UserPrincipal owner,
                                             @PathVariable Long venueId,
                                             @Valid @RequestBody VenueRequest request) {
        return ApiResponse.ok(venueManagementService.updateVenue(owner.id(), venueId, request));
    }

    @Operation(summary = "Tạm ngừng / mở lại cụm sân")
    @PatchMapping("/{venueId}/status")
    public ApiResponse<VenueResponse> changeStatus(@AuthenticationPrincipal UserPrincipal owner,
                                                   @PathVariable Long venueId,
                                                   @Valid @RequestBody VenueStatusRequest request) {
        return ApiResponse.ok(venueManagementService.changeVenueStatus(owner.id(), venueId, request.status()));
    }

    @Operation(summary = "Thêm sân con vào cụm sân")
    @PostMapping("/{venueId}/courts")
    public ResponseEntity<ApiResponse<CourtResponse>> addCourt(@AuthenticationPrincipal UserPrincipal owner,
                                                               @PathVariable Long venueId,
                                                               @Valid @RequestBody CourtCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(venueManagementService.addCourt(owner.id(), venueId, request)));
    }
}
