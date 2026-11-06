package com.sportbooking.service;

import com.sportbooking.entity.enums.SportType;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** "Bây giờ" được cố định = 10:00 ngày 06/11/2026, nên test luôn cho cùng kết quả bất kể chạy lúc nào. */
class BookingTimeValidatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final LocalTime OPEN = LocalTime.of(6, 0);
    private static final LocalTime CLOSE = LocalTime.of(23, 0);
    private static final int MAX_ADVANCE_DAYS = 30;
    private static final int MAX_SLOTS = 8;

    private static void validate(LocalDate date, String start, String end, SportType sportType) {
        BookingTimeValidator.validate(date, LocalTime.parse(start), LocalTime.parse(end), sportType,
                OPEN, CLOSE, NOW, MAX_ADVANCE_DAYS, MAX_SLOTS);
    }

    private static void assertInvalid(LocalDate date, String start, String end, SportType sportType, String messagePart) {
        assertThatThrownBy(() -> validate(date, start, end, sportType))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(messagePart)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BOOKING_TIME);
    }

    @Test
    void validFootballBooking_oneHour_passes() {
        assertThatCode(() -> validate(TOMORROW, "17:00", "18:00", SportType.FOOTBALL_5)).doesNotThrowAnyException();
    }

    @Test
    void laterToday_passes() {
        assertThatCode(() -> validate(TODAY, "10:30", "11:30", SportType.FOOTBALL_7)).doesNotThrowAnyException();
    }

    @Test
    void badminton_thirtyMinutes_passes() {
        assertThatCode(() -> validate(TOMORROW, "17:00", "17:30", SportType.BADMINTON)).doesNotThrowAnyException();
    }

    @Test
    void football_thirtyMinutes_isTooShort() {
        assertInvalid(TOMORROW, "17:00", "17:30", SportType.FOOTBALL_5, "tối thiểu 60 phút");
    }

    @Test
    void endNotAfterStart_isRejected() {
        assertInvalid(TOMORROW, "18:00", "17:00", SportType.BADMINTON, "sau giờ bắt đầu");
    }

    @Test
    void slotAlreadyPassedToday_isRejected() {
        assertInvalid(TODAY, "09:00", "10:00", SportType.FOOTBALL_5, "đã qua");
    }

    @Test
    void slotStartingExactlyNow_isRejected() {
        assertInvalid(TODAY, "10:00", "11:00", SportType.FOOTBALL_5, "đã qua");
    }

    @Test
    void tooFarInAdvance_isRejected() {
        assertInvalid(TODAY.plusDays(MAX_ADVANCE_DAYS + 1), "17:00", "18:00", SportType.FOOTBALL_5, "tối đa 30 ngày");
    }

    @Test
    void outsideOpeningHours_isRejected() {
        assertInvalid(TOMORROW, "05:30", "06:30", SportType.FOOTBALL_5, "mở cửa");
        assertInvalid(TOMORROW, "22:30", "23:30", SportType.BADMINTON, "mở cửa");
    }

    @Test
    void longerThanMax_isRejected() {
        assertInvalid(TOMORROW, "08:00", "12:30", SportType.FOOTBALL_5, "tối đa 240 phút");
    }
}
