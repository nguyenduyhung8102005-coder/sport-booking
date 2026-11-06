package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.dto.response.OwnerBookingResponse;
import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.repository.BookingSlotRepository;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingCancellationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private OwnerBookingService ownerBookingService;
    @Autowired
    private BookingSlotRepository bookingSlotRepository;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private TestDataFactory data;

    private LocalDate tomorrow() {
        return LocalDate.now(bookingProperties.zone()).plusDays(1);
    }

    private BookingResponse book(TestCourt court, User customer, LocalTime start) {
        return bookingService.createBooking(customer.getId(),
                new CreateBookingRequest(court.courtId(), tomorrow(), start, start.plusHours(1)));
    }

    // ===================== Khách hủy =====================

    @Test
    void customerCancelsPendingBooking_slotsAreReleasedImmediately() {
        TestCourt court = data.footballCourt();
        User customer = data.customer();
        BookingResponse booking = book(court, customer, LocalTime.of(17, 0));

        BookingResponse cancelled = bookingService.cancelMyBooking(customer.getId(), booking.id(), null);

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Khách hủy");
        assertThat(bookingSlotRepository.countByBookingId(booking.id())).isZero();
        // Người khác đặt lại được ngay, không phải chờ
        assertThat(book(court, data.customer(), LocalTime.of(17, 0)).status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void cancellingSomeoneElsesBooking_looksLikeNotFound() {
        TestCourt court = data.footballCourt();
        BookingResponse booking = book(court, data.customer(), LocalTime.of(17, 0));
        User stranger = data.customer();

        assertThatThrownBy(() -> bookingService.cancelMyBooking(stranger.getId(), booking.id(), null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void cancellingTwice_isRejected() {
        TestCourt court = data.footballCourt();
        User customer = data.customer();
        BookingResponse booking = book(court, customer, LocalTime.of(17, 0));
        bookingService.cancelMyBooking(customer.getId(), booking.id(), "Bận việc");

        assertThatThrownBy(() -> bookingService.cancelMyBooking(customer.getId(), booking.id(), "Bận việc"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BOOKING_STATE);
    }

    // ===================== Chủ sân =====================

    @Test
    void owner_seesDaySchedule_sortedByTime_butOtherOwnerCannot() {
        TestCourt court = data.footballCourt();
        book(court, data.customer(), LocalTime.of(19, 0));
        book(court, data.customer(), LocalTime.of(17, 0));

        List<OwnerBookingResponse> schedule = ownerBookingService.getVenueSchedule(court.ownerId(), court.venueId(), tomorrow());

        assertThat(schedule).extracting(OwnerBookingResponse::startTime)
                .containsExactly(LocalTime.of(17, 0), LocalTime.of(19, 0));
        assertThat(schedule).allSatisfy(item -> assertThat(item.customerName()).isNotBlank());

        TestCourt otherOwnersCourt = data.footballCourt();
        assertThatThrownBy(() -> ownerBookingService.getVenueSchedule(otherOwnersCourt.ownerId(), court.venueId(), tomorrow()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void owner_cancelsBookingWithReason_andOtherOwnerCannot() {
        TestCourt court = data.footballCourt();
        BookingResponse booking = book(court, data.customer(), LocalTime.of(17, 0));
        TestCourt otherOwnersCourt = data.footballCourt();

        assertThatThrownBy(() -> ownerBookingService.cancelByOwner(otherOwnersCourt.ownerId(), booking.id(), "Thử"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        OwnerBookingResponse cancelled = ownerBookingService.cancelByOwner(court.ownerId(), booking.id(), "Sân bảo trì");

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Chủ sân hủy: Sân bảo trì");
        assertThat(bookingSlotRepository.countByBookingId(booking.id())).isZero();
    }
}
