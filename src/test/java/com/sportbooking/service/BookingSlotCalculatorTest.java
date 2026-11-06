package com.sportbooking.service;

import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.PriceLabel;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingSlotCalculatorTest {

    // Bảng giá mẫu: ngày thường 06–17h 300k, 17–23h 500k; cuối tuần 06–23h 600k
    private static final List<PriceRule> RULES = List.of(
            rule(DayType.WEEKDAY, "06:00", "17:00", 300_000),
            rule(DayType.WEEKDAY, "17:00", "23:00", 500_000),
            rule(DayType.WEEKEND, "06:00", "23:00", 600_000));

    private static PriceRule rule(DayType dayType, String start, String end, long pricePerHour) {
        // court = null: không cần cho việc tính giá
        return new PriceRule(null, dayType, LocalTime.parse(start), LocalTime.parse(end), pricePerHour, PriceLabel.NORMAL);
    }

    private static List<LocalTime> times(String... values) {
        return java.util.Arrays.stream(values).map(LocalTime::parse).toList();
    }

    @Test
    void slotStarts_splitsRangeIntoHalfHours_endExclusive() {
        assertThat(BookingSlotCalculator.slotStarts(LocalTime.of(17, 0), LocalTime.of(18, 30)))
                .containsExactlyElementsOf(times("17:00", "17:30", "18:00"));
    }

    @Test
    void slotStarts_lateEvening_doesNotWrapPastMidnight() {
        assertThat(BookingSlotCalculator.slotStarts(LocalTime.of(22, 0), LocalTime.of(23, 30)))
                .containsExactlyElementsOf(times("22:00", "22:30", "23:00"));
    }

    @Test
    void totalPrice_bookingAcrossTwoPriceRanges_sumsEachSlot() {
        // 16:30 thuộc khung 300k/giờ (150k/slot), 17:00 thuộc khung 500k/giờ (250k/slot)
        long total = BookingSlotCalculator.totalPrice(times("16:30", "17:00"), DayType.WEEKDAY, RULES);

        assertThat(total).isEqualTo(400_000);
    }

    @Test
    void totalPrice_weekend_usesWeekendRules() {
        long total = BookingSlotCalculator.totalPrice(times("16:30", "17:00"), DayType.WEEKEND, RULES);

        assertThat(total).isEqualTo(600_000);
    }

    @Test
    void totalPrice_slotWithoutPrice_isRejected() {
        assertThatThrownBy(() -> BookingSlotCalculator.totalPrice(times("05:30", "06:00"), DayType.WEEKDAY, RULES))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("05:30")
                .extracting("errorCode").isEqualTo(ErrorCode.SLOT_NOT_PRICED);
    }

    @Test
    void findSlotPrice_rangeEndIsExclusive() {
        // 17:00 là điểm kết thúc của khung 300k nhưng là điểm bắt đầu của khung 500k
        assertThat(BookingSlotCalculator.findSlotPrice(LocalTime.of(16, 30), DayType.WEEKDAY, RULES)).isEqualTo(150_000);
        assertThat(BookingSlotCalculator.findSlotPrice(LocalTime.of(17, 0), DayType.WEEKDAY, RULES)).isEqualTo(250_000);
    }
}
