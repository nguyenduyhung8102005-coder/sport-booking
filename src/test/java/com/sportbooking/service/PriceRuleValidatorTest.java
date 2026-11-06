package com.sportbooking.service;

import com.sportbooking.dto.request.PriceRuleItem;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.PriceLabel;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test thuần Java: không Spring, không mock, không DB. */
class PriceRuleValidatorTest {

    private static final LocalTime OPEN = LocalTime.of(6, 0);
    private static final LocalTime CLOSE = LocalTime.of(23, 0);

    private static PriceRuleItem rule(DayType dayType, String start, String end, long price) {
        return new PriceRuleItem(dayType, LocalTime.parse(start), LocalTime.parse(end), price, PriceLabel.NORMAL);
    }

    private static void assertInvalid(List<PriceRuleItem> rules, String messagePart) {
        assertThatThrownBy(() -> PriceRuleValidator.validate(rules, OPEN, CLOSE))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(messagePart)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PRICE_RULES);
    }

    @Test
    void validTable_withAdjacentRanges_andBothDayTypes_passes() {
        List<PriceRuleItem> rules = List.of(
                rule(DayType.WEEKDAY, "17:00", "23:00", 500_000),   // cố ý không theo thứ tự
                rule(DayType.WEEKDAY, "06:00", "17:00", 300_000),   // 17:00 kết thúc = 17:00 bắt đầu: KHÔNG chồng lấn
                rule(DayType.WEEKEND, "06:00", "23:00", 550_000));

        assertThatCode(() -> PriceRuleValidator.validate(rules, OPEN, CLOSE)).doesNotThrowAnyException();
    }

    @Test
    void emptyTable_passes() {
        assertThatCode(() -> PriceRuleValidator.validate(List.of(), OPEN, CLOSE)).doesNotThrowAnyException();
    }

    @Test
    void overlappingRanges_onSameDayType_fails() {
        assertInvalid(List.of(
                rule(DayType.WEEKDAY, "06:00", "18:00", 300_000),
                rule(DayType.WEEKDAY, "17:00", "23:00", 500_000)), "chồng lấn");
    }

    @Test
    void sameRange_onDifferentDayTypes_isNotOverlap() {
        assertThatCode(() -> PriceRuleValidator.validate(List.of(
                rule(DayType.WEEKDAY, "06:00", "23:00", 300_000),
                rule(DayType.WEEKEND, "06:00", "23:00", 500_000)), OPEN, CLOSE))
                .doesNotThrowAnyException();
    }

    @Test
    void rangeOutsideOpeningHours_fails() {
        assertInvalid(List.of(rule(DayType.WEEKDAY, "05:00", "07:00", 300_000)), "ngoài giờ mở cửa");
    }

    @Test
    void endNotAfterStart_fails() {
        assertInvalid(List.of(rule(DayType.WEEKDAY, "17:00", "17:00", 300_000)), "sau giờ bắt đầu");
    }

    @Test
    void priceNotMultipleOfThousand_fails() {
        assertInvalid(List.of(rule(DayType.WEEKDAY, "06:00", "17:00", 300_500)), "bội số của 1.000đ");
    }
}
