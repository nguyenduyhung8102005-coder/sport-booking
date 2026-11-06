package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.dto.response.CourtAvailabilityResponse;
import com.sportbooking.service.CourtAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Public: SecurityConfig cho phép GET /api/v1/courts/** không cần đăng nhập. */
@Tag(name = "Court (public)", description = "Xem lịch trống của sân")
@RestController
@RequestMapping("/api/v1/courts")
@RequiredArgsConstructor
public class CourtController {

    private final CourtAvailabilityService courtAvailabilityService;

    @Operation(summary = "Lịch trống của 1 sân trong 1 ngày, theo slot 30 phút kèm giá")
    @GetMapping("/{courtId}/availability")
    public ApiResponse<CourtAvailabilityResponse> getAvailability(
            @PathVariable Long courtId,
            @Parameter(description = "Ngày cần xem, dạng yyyy-MM-dd", example = "2026-11-07")
            @RequestParam LocalDate date) {
        return ApiResponse.ok(courtAvailabilityService.getAvailability(courtId, date));
    }
}
