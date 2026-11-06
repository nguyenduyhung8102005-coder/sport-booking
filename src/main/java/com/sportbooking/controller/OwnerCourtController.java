package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.dto.request.CourtUpdateRequest;
import com.sportbooking.dto.request.PriceRulesRequest;
import com.sportbooking.dto.response.CourtResponse;
import com.sportbooking.dto.response.PriceRuleResponse;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.PriceRuleService;
import com.sportbooking.service.VenueManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Owner - Court", description = "Chủ sân sửa sân con và cấu hình bảng giá")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner/courts")
@RequiredArgsConstructor
public class OwnerCourtController {

    private final VenueManagementService venueManagementService;
    private final PriceRuleService priceRuleService;

    @Operation(summary = "Đổi tên / chuyển trạng thái sân con (ACTIVE, MAINTENANCE)")
    @PutMapping("/{courtId}")
    public ApiResponse<CourtResponse> update(@AuthenticationPrincipal UserPrincipal owner,
                                             @PathVariable Long courtId,
                                             @Valid @RequestBody CourtUpdateRequest request) {
        return ApiResponse.ok(venueManagementService.updateCourt(owner.id(), courtId, request));
    }

    @Operation(summary = "Xem bảng giá của sân con")
    @GetMapping("/{courtId}/price-rules")
    public ApiResponse<List<PriceRuleResponse>> getPriceRules(@AuthenticationPrincipal UserPrincipal owner,
                                                              @PathVariable Long courtId) {
        return ApiResponse.ok(priceRuleService.getPriceRules(owner.id(), courtId));
    }

    @Operation(summary = "Thay toàn bộ bảng giá của sân con",
            description = "Các khung giờ cùng loại ngày không được chồng lấn, phải nằm trong giờ mở cửa, "
                    + "giờ tròn :00/:30, giá là bội số 1.000đ. Slot không có giá sẽ không đặt được.")
    @PutMapping("/{courtId}/price-rules")
    public ApiResponse<List<PriceRuleResponse>> replacePriceRules(@AuthenticationPrincipal UserPrincipal owner,
                                                                  @PathVariable Long courtId,
                                                                  @Valid @RequestBody PriceRulesRequest request) {
        return ApiResponse.ok(priceRuleService.replacePriceRules(owner.id(), courtId, request.rules()));
    }
}
