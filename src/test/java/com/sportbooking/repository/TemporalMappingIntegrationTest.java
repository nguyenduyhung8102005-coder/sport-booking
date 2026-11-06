package com.sportbooking.repository;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.service.BookingService;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chống tái phát bug: giờ/ngày "địa phương" (LocalTime, LocalDate) phải được lưu NGUYÊN VẸN,
 * dù máy chạy app ở múi giờ nào.
 *
 * Bug đã gặp: cấu hình hibernate.jdbc.time_zone=UTC khiến Hibernate quy đổi LocalTime sang UTC
 * khi máy ở UTC+7 -> 06:00 bị lưu thành 23:00. Trên máy/CI chạy múi giờ UTC thì bug không lộ ra,
 * nên test này CỐ Ý đặt múi giờ JVM = Asia/Ho_Chi_Minh rồi đọc lại giá trị THÔ trong database.
 */
class TemporalMappingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestDataFactory data;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private JdbcTemplate jdbc;

    private TimeZone originalTimeZone;

    @BeforeEach
    void useVietnamTimeZone() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);   // không làm ảnh hưởng các test khác
    }

    @Test
    void localTimeAndLocalDate_areStoredExactlyAsGiven() {
        TestCourt court = data.footballCourt();   // mở cửa 06:00–23:00

        // Đọc giá trị dạng text ngay trong DB, không qua Hibernate (Hibernate đọc ra có thể "đổi ngược lại" che mất lỗi)
        assertThat(jdbc.queryForObject("SELECT open_time::text FROM venues WHERE id = ?", String.class, court.venueId()))
                .isEqualTo("06:00:00");
        assertThat(jdbc.queryForObject("SELECT close_time::text FROM venues WHERE id = ?", String.class, court.venueId()))
                .isEqualTo("23:00:00");

        LocalDate date = LocalDate.now(bookingProperties.zone()).plusDays(1);
        BookingResponse booking = bookingService.createBooking(data.customer().getId(),
                new CreateBookingRequest(court.courtId(), date, LocalTime.of(17, 0), LocalTime.of(18, 0)));

        assertThat(jdbc.queryForObject("SELECT booking_date::text FROM bookings WHERE id = ?", String.class, booking.id()))
                .isEqualTo(date.toString());
        assertThat(jdbc.queryForObject("SELECT start_time::text FROM bookings WHERE id = ?", String.class, booking.id()))
                .isEqualTo("17:00:00");
    }
}
