package com.sportbooking.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Loại sân và thời lượng đặt tối thiểu (tính bằng số slot 30 phút).
 * Thêm môn mới: thêm 1 dòng ở đây + migration sửa CHECK ck_courts_sport_type.
 */
@Getter
@RequiredArgsConstructor
public enum SportType {

    FOOTBALL_5(2),   // tối thiểu 2 slot = 1 tiếng
    FOOTBALL_7(2),   // tối thiểu 2 slot = 1 tiếng
    BADMINTON(1);    // tối thiểu 1 slot = 30 phút

    public static final int SLOT_MINUTES = 30;

    private final int minSlots;
}
