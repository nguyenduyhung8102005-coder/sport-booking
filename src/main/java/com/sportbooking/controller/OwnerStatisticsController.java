package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.dto.response.RevenueReportResponse;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.RevenueGroupBy;
import com.sportbooking.service.RevenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Owner - Statistics", description = "Thống kê doanh thu")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner/statistics")
@RequiredArgsConstructor
public class OwnerStatisticsController {

    private final RevenueService revenueService;

    @Operation(summary = "Doanh thu của cụm sân theo ngày hoặc theo sân con",
            description = "Chỉ tính lượt đã thanh toán (CONFIRMED, COMPLETED), theo ngày chơi. Tối đa 366 ngày.")
    @GetMapping("/revenue")
    public ApiResponse<RevenueReportResponse> revenue(
            @AuthenticationPrincipal UserPrincipal owner,
            @RequestParam Long venueId,
            @Parameter(example = "2026-11-01") @RequestParam LocalDate from,
            @Parameter(example = "2026-11-30") @RequestParam LocalDate to,
            @RequestParam(defaultValue = "DAY") RevenueGroupBy groupBy) {
        return ApiResponse.ok(revenueService.getRevenue(owner.id(), venueId, from, to, groupBy));
    }
}
