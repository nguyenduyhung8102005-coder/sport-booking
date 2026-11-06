package com.sportbooking.dto.request;

import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.SportType;
import com.sportbooking.validation.HalfHourTime;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Các bộ lọc tìm sân, tất cả đều không bắt buộc. Spring tự gom query param vào record này:
 * GET /api/v1/venues?city=Hà Nội&sportType=FOOTBALL_5&date=2026-11-07&startTime=17:00&endTime=18:30
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

        @Parameter(description = "Lọc giá theo ngày thường hay cuối tuần. Bỏ trống: suy ra từ 'date' nếu có, không thì cả hai")
        DayType dayType,

        @Parameter(description = "Giá tối thiểu (VND/giờ)")
        @PositiveOrZero(message = "Giá tối thiểu không được âm")
        Long minPrice,

        @Parameter(description = "Giá tối đa (VND/giờ)")
        @PositiveOrZero(message = "Giá tối đa không được âm")
        Long maxPrice,

        @Parameter(description = "Ngày muốn chơi (yyyy-MM-dd). Dùng cùng startTime, endTime để lọc sân còn trống")
        LocalDate date,

        @Parameter(description = "Giờ bắt đầu (HH:mm, tròn :00/:30)", example = "17:00")
        @HalfHourTime
        LocalTime startTime,

        @Parameter(description = "Giờ kết thúc (HH:mm, tròn :00/:30)", example = "18:30")
        @HalfHourTime
        LocalTime endTime) {
}
