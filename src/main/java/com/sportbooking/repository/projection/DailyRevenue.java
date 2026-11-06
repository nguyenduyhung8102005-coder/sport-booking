package com.sportbooking.repository.projection;

import java.time.LocalDate;

/** 1 dòng kết quả GROUP BY ngày. */
public record DailyRevenue(LocalDate date, Long bookingCount, Long revenue) {
}
