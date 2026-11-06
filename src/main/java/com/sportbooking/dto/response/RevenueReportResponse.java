package com.sportbooking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportbooking.service.RevenueGroupBy;

import java.time.LocalDate;
import java.util.List;

/** Báo cáo doanh thu. Chỉ tính lượt đã thanh toán (CONFIRMED, COMPLETED). */
public record RevenueReportResponse(
        Long venueId,
        LocalDate from,
        LocalDate to,
        RevenueGroupBy groupBy,
        long totalBookings,
        long totalRevenue,
        List<Item> items) {

    /** groupBy=DAY: có date. groupBy=COURT: có courtId, courtName. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            LocalDate date,
            Long courtId,
            String courtName,
            long bookingCount,
            long revenue) {
    }
}
