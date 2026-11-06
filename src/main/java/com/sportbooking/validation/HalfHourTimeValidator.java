package com.sportbooking.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalTime;

public class HalfHourTimeValidator implements ConstraintValidator<HalfHourTime, LocalTime> {

    @Override
    public boolean isValid(LocalTime value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;   // để @NotNull lo
        }
        return isHalfHour(value);
    }

    public static boolean isHalfHour(LocalTime time) {
        return time.getSecond() == 0
                && time.getNano() == 0
                && (time.getMinute() == 0 || time.getMinute() == 30);
    }
}
