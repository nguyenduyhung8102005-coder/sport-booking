package com.sportbooking.service;

import com.sportbooking.dto.response.RevenueReportResponse.Item;
import com.sportbooking.repository.projection.DailyRevenue;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RevenueFillMissingDaysTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    @Test
    void daysWithoutBookings_areFilledWithZero_inDateOrder() {
        List<DailyRevenue> rows = List.of(
                new DailyRevenue(NOV_1, 2L, 800_000L),
                new DailyRevenue(NOV_1.plusDays(3), 1L, 500_000L));

        List<Item> items = RevenueService.fillMissingDays(NOV_1, NOV_1.plusDays(3), rows);

        assertThat(items).extracting(Item::date)
                .containsExactly(NOV_1, NOV_1.plusDays(1), NOV_1.plusDays(2), NOV_1.plusDays(3));
        assertThat(items).extracting(Item::revenue).containsExactly(800_000L, 0L, 0L, 500_000L);
        assertThat(items).extracting(Item::bookingCount).containsExactly(2L, 0L, 0L, 1L);
    }

    @Test
    void singleDayRange_withNoData_returnsOneZeroRow() {
        assertThat(RevenueService.fillMissingDays(NOV_1, NOV_1, List.of()))
                .singleElement()
                .satisfies(item -> assertThat(item.revenue()).isZero());
    }
}
