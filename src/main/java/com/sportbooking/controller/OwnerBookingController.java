package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.dto.request.OwnerCancelBookingRequest;
import com.sportbooking.dto.response.OwnerBookingResponse;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.OwnerBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** /api/v1/owner/** chỉ dành cho role OWNER (SecurityConfig). */
@Tag(name = "Owner - Booking", description = "Chủ sân xem lịch đặt và hủy lượt đặt")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerBookingController {

    private final OwnerBookingService ownerBookingService;

    @Operation(summary = "Lịch đặt 1 ngày của cụm sân (mọi trạng thái, kèm liên hệ của khách)")
    @GetMapping("/venues/{venueId}/bookings")
    public ApiResponse<List<OwnerBookingResponse>> getSchedule(
            @AuthenticationPrincipal UserPrincipal owner,
            @PathVariable Long venueId,
            @Parameter(description = "Ngày cần xem, dạng yyyy-MM-dd", example = "2026-11-07")
            @RequestParam LocalDate date) {
        return ApiResponse.ok(ownerBookingService.getVenueSchedule(owner.id(), venueId, date));
    }

    @Operation(summary = "Chủ sân hủy lượt đặt (bắt buộc ghi lý do)")
    @PostMapping("/bookings/{bookingId}/cancel")
    public ApiResponse<OwnerBookingResponse> cancel(@AuthenticationPrincipal UserPrincipal owner,
                                                    @PathVariable Long bookingId,
                                                    @Valid @RequestBody OwnerCancelBookingRequest request) {
        return ApiResponse.ok(ownerBookingService.cancelByOwner(owner.id(), bookingId, request.reason()));
    }
}
