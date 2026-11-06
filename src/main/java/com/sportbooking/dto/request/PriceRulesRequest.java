package com.sportbooking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Thay TOÀN BỘ bảng giá của một sân (PUT = thay thế, không phải sửa từng dòng).
 * Danh sách rỗng = xóa bảng giá (sân sẽ không đặt được cho tới khi có giá).
 *
 * @Valid trên phần tử: validate cả từng PriceRuleItem bên trong.
 * Lỗi sẽ có field dạng "rules[2].startTime".
 */
public record PriceRulesRequest(
        @NotNull(message = "Danh sách giá không được để trống")
        @Size(max = 48, message = "Tối đa 48 khung giá")
        List<@Valid @NotNull PriceRuleItem> rules) {
}
