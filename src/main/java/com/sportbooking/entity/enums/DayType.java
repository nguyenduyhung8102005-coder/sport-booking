package com.sportbooking.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Getter
@RequiredArgsConstructor
public enum DayType {

    WEEKDAY("ngày thường"),
    WEEKEND("cuối tuần");

    private final String displayName;

    /** Thứ 7, Chủ nhật là cuối tuần. (Ngày lễ: phần mở rộng sau MVP.) */
    public static DayType of(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? WEEKEND : WEEKDAY;
    }
}
