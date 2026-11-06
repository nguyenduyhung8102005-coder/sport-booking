package com.sportbooking.dto.request;

import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.SportType;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Các bộ lọc tìm sân, tất cả đều không bắt buộc. Spring tự gom query param vào record này:
 * GET /api/v1/venues?city=Hà Nội&sportType=FOOTBALL_5&maxPrice=400000
 *
 * Lọc theo ngày + khung giờ còn trống sẽ thêm ở GĐ 4 (cần dữ liệu đặt sân).
 */
public record VenueSearchRequest(
        @Parameter(description = "Tìm theo tên cụm sân")
        @Size(max = 100, message = "Từ khóa tối đa 100 ký tự")
        String keyword,

        @Parameter(description = "Tỉnh/thành phố, ví dụ: Hà Nội")
        @Size(max = 100)
        String city,

        @Parameter(description = "Quận/huyện, ví dụ: Hà Đông")
        @Size(max = 100)
        String district,

        @Parameter(description = "Loại sân")
        SportType sportType,

        @Parameter(description = "Lọc giá theo ngày thường hay cuối tuần (bỏ trống = cả hai)")
        DayType dayType,

        @Parameter(description = "Giá tối thiểu (VND/giờ)")
        @PositiveOrZero(message = "Giá tối thiểu không được âm")
        Long minPrice,

        @Parameter(description = "Giá tối đa (VND/giờ)")
        @PositiveOrZero(message = "Giá tối đa không được âm")
        Long maxPrice) {
}
