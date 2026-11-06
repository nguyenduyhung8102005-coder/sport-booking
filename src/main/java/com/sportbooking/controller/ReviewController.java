package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.common.PageResponse;
import com.sportbooking.dto.request.ReviewRequest;
import com.sportbooking.dto.response.ReviewResponse;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.ReviewService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Review", description = "Đánh giá cụm sân")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Đánh giá lượt đặt đã chơi xong (mỗi lượt 1 lần)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/bookings/{bookingId}/review")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(@AuthenticationPrincipal UserPrincipal customer,
                                                              @PathVariable Long bookingId,
                                                              @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(reviewService.createReview(customer.id(), bookingId, request)));
    }

    @Operation(summary = "Danh sách đánh giá của cụm sân (public)")
    @GetMapping("/venues/{venueId}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> list(
            @PathVariable Long venueId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(reviewService.getVenueReviews(venueId, pageable));
    }
}
