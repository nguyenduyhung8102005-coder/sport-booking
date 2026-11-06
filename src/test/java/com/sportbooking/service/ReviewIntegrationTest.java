package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.request.ReviewRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.dto.response.ReviewResponse;
import com.sportbooking.entity.User;
import com.sportbooking.entity.Venue;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.repository.VenueRepository;
import com.sportbooking.support.ConcurrencyRunner;
import com.sportbooking.support.ConcurrencyRunner.Outcome;
import com.sportbooking.support.IntegrationTestBase;
import com.sportbooking.support.TestDataFactory;
import com.sportbooking.support.TestDataFactory.TestCourt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private TestDataFactory data;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private record PlayedBooking(User customer, Long bookingId) {
    }

    /** Khách đặt sân, rồi dùng SQL đánh dấu đã chơi xong (COMPLETED). */
    private PlayedBooking completedBooking(TestCourt court, LocalDate date, LocalTime start) {
        User customer = data.customer();
        BookingResponse booking = bookingService.createBooking(customer.getId(),
                new CreateBookingRequest(court.courtId(), date, start, start.plusHours(1)));
        jdbc.update("UPDATE bookings SET status = 'COMPLETED' WHERE id = ?", booking.id());
        return new PlayedBooking(customer, booking.id());
    }

    private LocalDate tomorrow() {
        return LocalDate.now(bookingProperties.zone()).plusDays(1);
    }

    @Test
    void reviewOfCompletedBooking_isSaved_andUpdatesVenueRating() {
        TestCourt court = data.footballCourt();
        PlayedBooking played = completedBooking(court, tomorrow(), LocalTime.of(17, 0));

        ReviewResponse review = reviewService.createReview(played.customer().getId(), played.bookingId(),
                new ReviewRequest(4, "  Sân đẹp, cỏ mới  "));

        assertThat(review.rating()).isEqualTo(4);
        assertThat(review.comment()).isEqualTo("Sân đẹp, cỏ mới");
        Venue venue = venueRepository.findById(court.venueId()).orElseThrow();
        assertThat(venue.getReviewCount()).isEqualTo(1);
        assertThat(venue.getAvgRating()).isEqualByComparingTo("4.0");
    }

    @Test
    void cannotReview_pendingBooking_someoneElsesBooking_orTwice() {
        TestCourt court = data.footballCourt();
        User customer = data.customer();
        BookingResponse pending = bookingService.createBooking(customer.getId(),
                new CreateBookingRequest(court.courtId(), tomorrow(), LocalTime.of(8, 0), LocalTime.of(9, 0)));
        assertThatThrownBy(() -> reviewService.createReview(customer.getId(), pending.id(), new ReviewRequest(5, null)))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);

        PlayedBooking played = completedBooking(court, tomorrow(), LocalTime.of(17, 0));
        User stranger = data.customer();
        assertThatThrownBy(() -> reviewService.createReview(stranger.getId(), played.bookingId(), new ReviewRequest(5, null)))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        reviewService.createReview(played.customer().getId(), played.bookingId(), new ReviewRequest(5, null));
        assertThatThrownBy(() -> reviewService.createReview(played.customer().getId(), played.bookingId(), new ReviewRequest(1, null)))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    /**
     * ⭐ 20 khách cùng đánh giá 1 cụm sân CÙNG LÚC (số sao 1..5, tổng 60).
     * Nhờ UPDATE nguyên tử, không lượt nào bị mất: 20 đánh giá, trung bình 3.0.
     */
    @Test
    void twentyConcurrentReviews_areAllCounted_noLostUpdate() throws InterruptedException {
        TestCourt court = data.footballCourt();
        List<PlayedBooking> played = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            LocalDate date = tomorrow().plusDays(i / 10);                      // 10 lượt mỗi ngày
            played.add(completedBooking(court, date, LocalTime.of(6, 0).plusHours(i % 10)));
        }

        List<Outcome<ReviewResponse>> outcomes = ConcurrencyRunner.run(20, i -> () -> reviewService.createReview(
                played.get(i).customer().getId(), played.get(i).bookingId(), new ReviewRequest(i % 5 + 1, null)));

        assertThat(outcomes).allMatch(Outcome::isSuccess);
        Venue venue = venueRepository.findById(court.venueId()).orElseThrow();
        assertThat(venue.getReviewCount()).isEqualTo(20);
        assertThat(venue.getRatingTotal()).isEqualTo(60);
        assertThat(venue.getAvgRating()).isEqualByComparingTo("3.0");
        assertThat(reviewService.getVenueReviews(court.venueId(), PageRequest.of(0, 5)).totalElements()).isEqualTo(20);
    }

    /**
     * 🧪 Đối chứng: cách "đọc - tính - ghi" trong Java làm MẤT lượt đánh giá khi chạy đồng thời.
     * (Cố ý viết sai bằng SQL thuần trên bảng demo để thấy tận mắt vấn đề mà addRating giải quyết.)
     */
    @Test
    void readModifyWrite_losesUpdates_underConcurrency() throws InterruptedException {
        jdbc.execute("CREATE TABLE IF NOT EXISTS demo_counter (id INT PRIMARY KEY, review_count INT NOT NULL)");
        jdbc.execute("DELETE FROM demo_counter");
        jdbc.update("INSERT INTO demo_counter VALUES (1, 0)");

        ConcurrencyRunner.run(20, i -> () -> transactionTemplate.execute(status -> {
            Integer current = jdbc.queryForObject("SELECT review_count FROM demo_counter WHERE id = 1", Integer.class);
            sleepQuietly(30);   // khoảng thời gian giữa "đọc" và "ghi"
            return jdbc.update("UPDATE demo_counter SET review_count = ? WHERE id = 1", current + 1);
        }));

        Integer finalCount = jdbc.queryForObject("SELECT review_count FROM demo_counter WHERE id = 1", Integer.class);
        System.out.printf("[LAB] đọc-tính-ghi: 20 lượt đánh giá nhưng chỉ ghi nhận được %d%n", finalCount);
        assertThat(finalCount).isLessThan(20);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
