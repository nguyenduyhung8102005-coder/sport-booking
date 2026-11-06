package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingCompletionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingCompletionService bookingCompletionService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private TestDataFactory data;
    @Autowired
    private JdbcTemplate jdbc;

    /** Tạo lượt đặt rồi dùng SQL đưa về trạng thái mong muốn (không thể đặt sân ở quá khứ qua API). */
    private Long confirmedBookingOn(TestCourt court, LocalDate date) {
        BookingResponse booking = bookingService.createBooking(data.customer().getId(), new CreateBookingRequest(
                court.courtId(), LocalDate.now(bookingProperties.zone()).plusDays(1), LocalTime.of(17, 0), LocalTime.of(18, 0)));
        jdbc.update("UPDATE bookings SET status = 'CONFIRMED', booking_date = ? WHERE id = ?", date, booking.id());
        return booking.id();
    }

    private long versionOf(Long bookingId) {
        return jdbc.queryForObject("SELECT version FROM bookings WHERE id = ?", Long.class, bookingId);
    }

    @Test
    void confirmedBookingInThePast_becomesCompleted_andVersionIsBumped() {
        Long bookingId = confirmedBookingOn(data.footballCourt(), LocalDate.now(bookingProperties.zone()).minusDays(1));
        long versionBefore = versionOf(bookingId);

        int completed = bookingCompletionService.completeFinishedBookings();

        assertThat(completed).isGreaterThanOrEqualTo(1);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        // Bulk update tự tăng version: ai đang giữ bản cũ của booking sẽ gặp optimistic lock khi lưu
        assertThat(versionOf(bookingId)).isEqualTo(versionBefore + 1);
    }

    @Test
    void confirmedBookingInTheFuture_isLeftUntouched() {
        Long bookingId = confirmedBookingOn(data.footballCourt(), LocalDate.now(bookingProperties.zone()).plusDays(2));

        bookingCompletionService.completeFinishedBookings();

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
