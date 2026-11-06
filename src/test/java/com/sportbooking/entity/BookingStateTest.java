package com.sportbooking.entity;

import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Máy trạng thái của Booking: test thuần Java, không cần database. */
class BookingStateTest {

    private static final Instant NOW = Instant.parse("2026-11-06T05:00:00Z");

    private static Booking pendingExpiringAt(Instant expiresAt) {
        return Booking.newPending("BKTEST", null, null, LocalDate.of(2026, 11, 7),
                LocalTime.of(17, 0), LocalTime.of(18, 0), 500_000, expiresAt);
    }

    @Test
    void pendingBooking_isOverdueOnlyAfterExpiresAt() {
        assertThat(pendingExpiringAt(NOW.plusSeconds(60)).isOverdue(NOW)).isFalse();
        assertThat(pendingExpiringAt(NOW.minusSeconds(1)).isOverdue(NOW)).isTrue();
    }

    @Test
    void expire_movesPendingToExpired_andReleasesHold() {
        Booking booking = pendingExpiringAt(NOW.minusSeconds(1));

        booking.expire();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.holdsSlots()).isFalse();
        assertThat(booking.isOverdue(NOW)).isFalse();   // đã EXPIRED thì không còn "quá hạn chờ xử lý"
    }

    @Test
    void cancel_recordsReasonAndTime() {
        Booking booking = pendingExpiringAt(NOW.plusSeconds(600));

        booking.cancel("Đổi lịch", NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelReason()).isEqualTo("Đổi lịch");
        assertThat(booking.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void invalidTransitions_areRejected() {
        Booking expired = pendingExpiringAt(NOW.minusSeconds(1));
        expired.expire();

        assertThatThrownBy(expired::expire)
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BOOKING_STATE);
        assertThatThrownBy(() -> expired.cancel("muộn rồi", NOW))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BOOKING_STATE);
    }
}
