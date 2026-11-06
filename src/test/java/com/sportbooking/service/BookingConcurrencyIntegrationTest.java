package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.entity.User;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.BookingSlotRepository;
import com.sportbooking.support.ConcurrencyRunner;
import com.sportbooking.support.ConcurrencyRunner.Outcome;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⭐ CHỨNG MINH CHỐNG ĐẶT TRÙNG: gọi BookingService thật, với PostgreSQL thật, từ 50 thread cùng lúc.
 */
class BookingConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final int THREADS = 50;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingSlotRepository bookingSlotRepository;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private TestDataFactory data;

    private TestCourt court;
    private LocalDate date;
    private List<User> customers;

    @BeforeEach
    void setUp() {
        court = data.footballCourt();
        date = LocalDate.now(bookingProperties.zone()).plusDays(1);
        customers = data.customers(THREADS);   // 50 khách khác nhau (tránh giới hạn lượt đặt chờ thanh toán)
    }

    private CreateBookingRequest request(String start, String end) {
        return new CreateBookingRequest(court.courtId(), date, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void fiftyCustomersBookTheSameSlotAtOnce_exactlyOneSucceeds() throws InterruptedException {
        List<Outcome<BookingResponse>> outcomes = ConcurrencyRunner.run(THREADS,
                i -> () -> bookingService.createBooking(customers.get(i).getId(), request("17:00", "18:00")));

        assertExactlyOneWinner(outcomes);
        // Database: đúng 1 booking, đúng 2 slot (17:00, 17:30). Các request thua đã rollback sạch.
        assertThat(bookingRepository.countByCourtId(court.courtId())).isEqualTo(1);
        assertThat(bookingSlotRepository.countByCourtIdAndSlotDate(court.courtId(), date)).isEqualTo(2);
    }

    @Test
    void overlappingButDifferentRanges_exactlyOneSucceeds() throws InterruptedException {
        // Một nửa đặt 17:00–18:00, nửa kia đặt 17:30–18:30. Hai khoảng giao nhau ở slot 17:30.
        List<Outcome<BookingResponse>> outcomes = ConcurrencyRunner.run(THREADS,
                i -> () -> bookingService.createBooking(customers.get(i).getId(),
                        i % 2 == 0 ? request("17:00", "18:00") : request("17:30", "18:30")));

        assertExactlyOneWinner(outcomes);
        assertThat(bookingRepository.countByCourtId(court.courtId())).isEqualTo(1);
        assertThat(bookingSlotRepository.countByCourtIdAndSlotDate(court.courtId(), date)).isEqualTo(2);
    }

    @Test
    void adjacentRanges_doNotBlockEachOther() throws InterruptedException {
        // 17:00–18:00 và 18:00–19:00 chỉ "chạm" nhau, không giao -> cả 2 phải thành công.
        // Chứng minh cách chống trùng không chặn nhầm (không khóa cả sân như pessimistic lock).
        List<Outcome<BookingResponse>> outcomes = ConcurrencyRunner.run(2,
                i -> () -> bookingService.createBooking(customers.get(i).getId(),
                        i == 0 ? request("17:00", "18:00") : request("18:00", "19:00")));

        assertThat(outcomes).allMatch(Outcome::isSuccess);
        assertThat(bookingSlotRepository.countByCourtIdAndSlotDate(court.courtId(), date)).isEqualTo(4);
    }

    private static void assertExactlyOneWinner(List<Outcome<BookingResponse>> outcomes) {
        assertThat(outcomes).hasSize(THREADS);
        assertThat(outcomes).filteredOn(Outcome::isSuccess).hasSize(1);
        // Mọi request thua đều nhận ĐÚNG lỗi nghiệp vụ SLOT_ALREADY_BOOKED (sẽ thành HTTP 409),
        // không có lỗi 500, deadlock hay timeout nào.
        assertThat(outcomes).filteredOn(outcome -> !outcome.isSuccess())
                .allSatisfy(outcome -> assertThat(outcome.error())
                        .isInstanceOf(AppException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.SLOT_ALREADY_BOOKED));
    }
}
