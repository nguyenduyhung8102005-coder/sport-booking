package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.common.PageResponse;
import com.sportbooking.dto.request.VenueSearchRequest;
import com.sportbooking.dto.response.VenueDetailResponse;
import com.sportbooking.dto.response.VenueSummaryResponse;
import com.sportbooking.service.VenueQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API công khai, không cần đăng nhập (SecurityConfig cho phép GET /api/v1/venues/**). */
@Tag(name = "Venue (public)", description = "Tìm kiếm và xem chi tiết cụm sân")
@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueQueryService venueQueryService;

    @Operation(summary = "Tìm cụm sân",
            description = "Lọc theo tên, tỉnh/thành, quận/huyện, loại sân, khoảng giá (VND/giờ). "
                    + "Sắp xếp được theo: avgRating, reviewCount, name, createdAt. Ví dụ: sort=avgRating,desc")
    @GetMapping
    public ApiResponse<PageResponse<VenueSummaryResponse>> search(
            @Valid @ParameterObject VenueSearchRequest criteria,
            @ParameterObject @PageableDefault(size = 10, sort = "avgRating", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(venueQueryService.search(criteria, pageable));
    }

    @Operation(summary = "Chi tiết cụm sân: các sân con đang hoạt động và bảng giá")
    @GetMapping("/{venueId}")
    public ApiResponse<VenueDetailResponse> getDetail(@PathVariable Long venueId) {
        return ApiResponse.ok(venueQueryService.getPublicDetail(venueId));
    }
}
