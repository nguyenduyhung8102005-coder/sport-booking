package com.sportbooking.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @ParameterizedTest: chạy cùng 1 test với nhiều giá trị đầu vào,
 * thay vì viết 8 method test gần giống nhau.
 */
class HalfHourTimeValidatorTest {

    private final HalfHourTimeValidator validator = new HalfHourTimeValidator();

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "06:00", "06:30", "17:00", "23:30"})
    void roundHalfHours_areValid(String time) {
        assertThat(validator.isValid(LocalTime.parse(time), null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"06:15", "06:45", "17:01", "17:00:30"})
    void otherTimes_areInvalid(String time) {
        assertThat(validator.isValid(LocalTime.parse(time), null)).isFalse();
    }

    @Test
    void null_isConsideredValid_letNotNullHandleIt() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
