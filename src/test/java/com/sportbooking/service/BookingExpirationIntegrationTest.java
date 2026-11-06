package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.BookingSlotRepository;
import com.sportbooking.scheduler.BookingExpirationJob;
import com.sportbooking.support.ConcurrencyRunner;
import com.sportbooking.support.ConcurrencyRunner.Outcome;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hết hạn giữ chỗ. Không đợi 10 phút thật: dùng SQL đẩy expires_at về quá khứ rồi gọi job trực tiếp
 * (trong profile test, job không tự chạy theo lịch).
 */
class BookingExpirationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingExpirationService bookingExpirationService;
    @Autowired
    private BookingExpirationJob bookingExpirationJob;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingSlotRepository bookingSlotRepository;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private TestDataFactory data;
    @Autowired
    private JdbcTemplate jdbc;

    private LocalDate tomorrow() {
        return LocalDate.now(bookingProperties.zone()).plusDays(1);
    }

    private BookingResponse book(TestCourt court, User customer, LocalDate date, LocalTime start) {
        return bookingService.createBooking(customer.getId(),
                new CreateBookingRequest(court.courtId(), date, start, start.plusHours(1)));
    }

    private void makeOverdue(Long bookingId) {
        jdbc.update("UPDATE bookings SET expires_at = now() - interval '1 minute' WHERE id = ?", bookingId);
    }

    private BookingStatus statusOf(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    @Test
    void job_expiresOverdueBooking_andReleasesItsSlots_soAnotherCustomerCanBook() {
        TestCourt court = data.footballCourt();
        LocalDate date = tomorrow();
        BookingResponse booking = book(court, data.customer(), date, LocalTime.of(17, 0));
        makeOverdue(booking.id());

        int expired = bookingExpirationJob.run();

        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(statusOf(booking.id())).isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingSlotRepository.countByBookingId(booking.id())).isZero();

        // Slot 17:00 đã được nhả -> khách khác đặt được ngay
        BookingResponse rebooked = book(court, data.customer(), date, LocalTime.of(17, 0));
        assertThat(rebooked.status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void bookingStillWithinHoldTime_isLeftUntouched() {
        TestCourt court = data.footballCourt();
        BookingResponse booking = book(court, data.customer(), tomorrow(), LocalTime.of(17, 0));

        boolean expired = bookingExpirationService.expireIfOverdue(booking.id());

        assertThat(expired).isFalse();
        assertThat(statusOf(booking.id())).isEqualTo(BookingStatus.PENDING);
        assertThat(bookingSlotRepository.countByBookingId(booking.id())).isEqualTo(2);
    }

    /**
     * ⭐ Cuộc đua: job hết hạn và khách bấm "Hủy" CÙNG LÚC trên cùng 1 booking quá hạn.
     * Lặp 10 lần để có nhiều kiểu xen kẽ khác nhau. Mỗi lần, ĐÚNG MỘT bên được thắng,
     * trạng thái cuối khớp với bên thắng, slot được nhả đúng 1 lần, bên thua thất bại "đúng kiểu".
     */
    @Test
    void jobAndCustomerCancellingAtTheSameTime_exactlyOneTransitionWins() throws InterruptedException {
        TestCourt court = data.footballCourt();
        LocalDate date = tomorrow();
        int jobWins = 0;
        int cancelWins = 0;

        for (int round = 0; round < 10; round++) {
            User customer = data.customer();
            BookingResponse booking = book(court, customer, date, LocalTime.of(7, 0).plusHours(round));
            makeOverdue(booking.id());

            List<Outcome<Object>> outcomes = ConcurrencyRunner.<Object>run(2, i -> i == 0
                    ? () -> bookingExpirationService.expireIfOverdue(booking.id())
                    : () -> bookingService.cancelMyBooking(customer.getId(), booking.id(), "Đổi lịch"));

            Outcome<Object> job = outcomes.get(0);
            Outcome<Object> cancel = outcomes.get(1);
            boolean jobWon = job.isSuccess() && Boolean.TRUE.equals(job.value());
            boolean cancelWon = cancel.isSuccess();

            assertThat(jobWon ^ cancelWon).as("vòng %d: phải có đúng một bên thắng", round).isTrue();
            assertThat(statusOf(booking.id())).isEqualTo(jobWon ? BookingStatus.EXPIRED : BookingStatus.CANCELLED);
            assertThat(bookingSlotRepository.countByBookingId(booking.id())).isZero();

            // Bên thua phải thua vì đúng lý do: thấy trạng thái đã đổi, hoặc lệch version (optimistic lock)
            if (!job.isSuccess()) {
                assertThat(job.error()).isInstanceOf(OptimisticLockingFailureException.class);
            }
            if (!cancel.isSuccess()) {
                assertThat(cancel.error()).isInstanceOfAny(OptimisticLockingFailureException.class, AppException.class);
            }

            if (jobWon) {
                jobWins++;
            } else {
                cancelWins++;
            }
        }
        System.out.printf("[RACE] job thắng %d lần, khách hủy thắng %d lần, không lần nào cả hai cùng thắng%n",
                jobWins, cancelWins);
    }
}
