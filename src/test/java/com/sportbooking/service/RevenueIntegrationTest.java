package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.dto.response.RevenueReportResponse;
import com.sportbooking.dto.response.RevenueReportResponse.Item;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dữ liệu:
 *   ngày D   : sân 1 CONFIRMED (tính), sân 1 PENDING (không tính)
 *   ngày D+1 : không có gì -> dòng 0đ
 *   ngày D+2 : sân 2 COMPLETED (tính), sân 1 CANCELLED (không tính)
 */
class RevenueIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private RevenueService revenueService;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private TestDataFactory data;
    @Autowired
    private JdbcTemplate jdbc;

    private TestCourt court;
    private Long secondCourtId;
    private LocalDate day;
    private BookingResponse confirmedOnDay;
    private BookingResponse completedOnDay2;

    private BookingResponse book(Long courtId, LocalDate date, LocalTime start, String status) {
        BookingResponse booking = bookingService.createBooking(data.customer().getId(),
                new CreateBookingRequest(courtId, date, start, start.plusHours(1)));
        if (status != null) {
            jdbc.update("UPDATE bookings SET status = ? WHERE id = ?", status, booking.id());
        }
        return booking;
    }

    @BeforeEach
    void setUp() {
        court = data.footballCourt();
        secondCourtId = data.addFootballCourt(court, "Sân 2");
        day = LocalDate.now(bookingProperties.zone()).plusDays(1);

        confirmedOnDay = book(court.courtId(), day, LocalTime.of(17, 0), "CONFIRMED");
        book(court.courtId(), day, LocalTime.of(8, 0), null);                                 // PENDING
        completedOnDay2 = book(secondCourtId, day.plusDays(2), LocalTime.of(17, 0), "COMPLETED");
        book(court.courtId(), day.plusDays(2), LocalTime.of(19, 0), "CANCELLED");
    }

    @Test
    void revenueByDay_countsOnlyPaidBookings_andFillsEmptyDays() {
        RevenueReportResponse report = revenueService.getRevenue(court.ownerId(), court.venueId(),
                day, day.plusDays(2), RevenueGroupBy.DAY);

        assertThat(report.items()).extracting(Item::date).containsExactly(day, day.plusDays(1), day.plusDays(2));
        assertThat(report.items()).extracting(Item::revenue)
                .containsExactly(confirmedOnDay.totalPrice(), 0L, completedOnDay2.totalPrice());
        assertThat(report.totalBookings()).isEqualTo(2);
        assertThat(report.totalRevenue()).isEqualTo(confirmedOnDay.totalPrice() + completedOnDay2.totalPrice());
    }

    @Test
    void revenueByCourt_groupsPerCourt() {
        RevenueReportResponse report = revenueService.getRevenue(court.ownerId(), court.venueId(),
                day, day.plusDays(2), RevenueGroupBy.COURT);

        assertThat(report.items()).hasSize(2);
        assertThat(report.items()).filteredOn(item -> item.courtId().equals(court.courtId()))
                .singleElement().satisfies(item -> assertThat(item.revenue()).isEqualTo(confirmedOnDay.totalPrice()));
        assertThat(report.items()).filteredOn(item -> item.courtId().equals(secondCourtId))
                .singleElement().satisfies(item -> assertThat(item.revenue()).isEqualTo(completedOnDay2.totalPrice()));
    }

    @Test
    void otherOwner_orInvalidRange_isRejected() {
        TestCourt otherOwnersCourt = data.footballCourt();
        assertThatThrownBy(() -> revenueService.getRevenue(otherOwnersCourt.ownerId(), court.venueId(),
                day, day.plusDays(2), RevenueGroupBy.DAY))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        assertThatThrownBy(() -> revenueService.getRevenue(court.ownerId(), court.venueId(),
                day, day.minusDays(1), RevenueGroupBy.DAY))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }
}
