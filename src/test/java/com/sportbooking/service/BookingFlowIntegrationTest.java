package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.request.VenueSearchRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.dto.response.CourtAvailabilityResponse;
import com.sportbooking.dto.response.CourtAvailabilityResponse.SlotStatus;
import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Luồng đặt sân từ đầu đến cuối với database thật. */
class BookingFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private CourtAvailabilityService courtAvailabilityService;
    @Autowired
    private VenueQueryService venueQueryService;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private TestDataFactory data;

    /** Ngày thường gần nhất kể từ ngày mai (bảng giá test khác nhau giữa ngày thường và cuối tuần). */
    private LocalDate nextWeekday() {
        LocalDate date = LocalDate.now(bookingProperties.zone()).plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static CreateBookingRequest request(TestCourt court, LocalDate date, String start, String end) {
        return new CreateBookingRequest(court.courtId(), date, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void booking_spanningTwoPriceRanges_isPricedPerSlot_andShowsAsBooked() {
        TestCourt court = data.footballCourt();
        User customer = data.customer();
        LocalDate date = nextWeekday();

        // 16:30–17:30: slot 16:30 giá 300k/giờ, slot 17:00 giá 500k/giờ -> 150k + 250k
        BookingResponse booking = bookingService.createBooking(customer.getId(), request(court, date, "16:30", "17:30"));

        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.totalPrice()).isEqualTo(400_000);
        assertThat(booking.expiresAt()).isNotNull();
        assertThat(booking.bookingCode()).startsWith("BK");

        CourtAvailabilityResponse availability = courtAvailabilityService.getAvailability(court.courtId(), date);
        Map<LocalTime, SlotStatus> statusByStart = availability.slots().stream()
                .collect(Collectors.toMap(CourtAvailabilityResponse.Slot::startTime, CourtAvailabilityResponse.Slot::status));
        assertThat(statusByStart.get(LocalTime.of(16, 30))).isEqualTo(SlotStatus.BOOKED);
        assertThat(statusByStart.get(LocalTime.of(17, 0))).isEqualTo(SlotStatus.BOOKED);
        assertThat(statusByStart.get(LocalTime.of(17, 30))).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void bookingAnAlreadyTakenSlot_sequentially_returnsFriendlyMessage() {
        TestCourt court = data.footballCourt();
        LocalDate date = nextWeekday();
        bookingService.createBooking(data.customer().getId(), request(court, date, "17:00", "18:00"));

        assertThatThrownBy(() -> bookingService.createBooking(data.customer().getId(), request(court, date, "17:30", "18:30")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("17:30")
                .extracting("errorCode").isEqualTo(ErrorCode.SLOT_ALREADY_BOOKED);
    }

    @Test
    void search_withTimeRange_hidesVenueWhoseOnlyCourtIsBusy() {
        TestCourt court = data.footballCourt();
        LocalDate date = nextWeekday();
        bookingService.createBooking(data.customer().getId(), request(court, date, "17:00", "18:00"));

        assertThat(searchByNameAndTime(court, date, "17:30", "18:30")).isZero();   // trùng slot 17:30
        assertThat(searchByNameAndTime(court, date, "18:00", "19:00")).isOne();    // còn trống
    }

    @Test
    void customer_cannotHoldMoreThanMaxPendingBookings() {
        TestCourt court = data.footballCourt();
        LocalDate date = nextWeekday();
        User customer = data.customer();
        int max = bookingProperties.maxPendingPerCustomer();

        for (int i = 0; i < max; i++) {
            LocalTime start = LocalTime.of(8, 0).plusHours(i);
            bookingService.createBooking(customer.getId(),
                    new CreateBookingRequest(court.courtId(), date, start, start.plusHours(1)));
        }

        assertThatThrownBy(() -> bookingService.createBooking(customer.getId(), request(court, date, "20:00", "21:00")))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_PENDING_BOOKINGS);
    }

    private long searchByNameAndTime(TestCourt court, LocalDate date, String start, String end) {
        VenueSearchRequest criteria = new VenueSearchRequest(court.venueName(), null, null, null, null, null, null,
                date, LocalTime.parse(start), LocalTime.parse(end));
        return venueQueryService.search(criteria, PageRequest.of(0, 10)).totalElements();
    }
}
