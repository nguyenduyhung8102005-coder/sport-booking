package com.sportbooking.service;

import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationPolicyTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 18, 0);
    private static final Duration DEADLINE = Duration.ofHours(24);

    @Test
    void pending_canBeCancelledByCustomer_evenOneMinuteBeforeStart() {
        assertThatCode(() -> CancellationPolicy.checkCustomerCancellation(
                BookingStatus.PENDING, START, START.minusMinutes(1), DEADLINE)).doesNotThrowAnyException();
    }

    @Test
    void confirmed_canBeCancelledByCustomer_moreThan24hBefore() {
        assertThatCode(() -> CancellationPolicy.checkCustomerCancellation(
                BookingStatus.CONFIRMED, START, START.minusHours(25), DEADLINE)).doesNotThrowAnyException();
    }

    @Test
    void confirmed_cannotBeCancelledByCustomer_within24h() {
        assertThatThrownBy(() -> CancellationPolicy.checkCustomerCancellation(
                BookingStatus.CONFIRMED, START, START.minusHours(23), DEADLINE))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("24 giờ")
                .extracting("errorCode").isEqualTo(ErrorCode.CANCELLATION_NOT_ALLOWED);
    }

    @Test
    void confirmed_canBeCancelledByOwner_within24h() {
        assertThatCode(() -> CancellationPolicy.checkOwnerCancellation(
                BookingStatus.CONFIRMED, START, START.minusHours(2))).doesNotThrowAnyException();
    }

    @Test
    void bookingAlreadyStarted_cannotBeCancelled() {
        assertThatThrownBy(() -> CancellationPolicy.checkCustomerCancellation(
                BookingStatus.PENDING, START, START, DEADLINE))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANCELLATION_NOT_ALLOWED);
        assertThatThrownBy(() -> CancellationPolicy.checkOwnerCancellation(
                BookingStatus.CONFIRMED, START, START.plusMinutes(10)))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANCELLATION_NOT_ALLOWED);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "EXPIRED", "COMPLETED"})
    void finishedBookings_cannotBeCancelled(BookingStatus status) {
        assertThatThrownBy(() -> CancellationPolicy.checkCustomerCancellation(
                status, START, START.minusDays(3), DEADLINE))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BOOKING_STATE);
    }
}
