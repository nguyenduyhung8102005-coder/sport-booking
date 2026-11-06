package com.sportbooking.service;

import com.sportbooking.entity.enums.SportType;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Các quy tắc về thời gian của 1 lượt đặt. Java thuần -> unit test không cần Spring hay DB.
 * "now" được truyền vào (không tự gọi LocalDateTime.now()) để test được mọi tình huống thời gian.
 */
public final class BookingTimeValidator {

    private BookingTimeValidator() {
    }

    public static void validate(LocalDate date, LocalTime start, LocalTime end, SportType sportType,
                                LocalTime openTime, LocalTime closeTime,
                                LocalDateTime now, int maxAdvanceDays, int maxSlots) {
        if (!end.isAfter(start)) {
            fail("Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (!LocalDateTime.of(date, start).isAfter(now)) {
            fail("Không thể đặt khung giờ đã qua");
        }
        if (date.isAfter(now.toLocalDate().plusDays(maxAdvanceDays))) {
            fail("Chỉ được đặt trước tối đa " + maxAdvanceDays + " ngày");
        }
        if (start.isBefore(openTime) || end.isAfter(closeTime)) {
            fail("Sân chỉ mở cửa từ " + openTime + " đến " + closeTime);
        }

        long slots = Duration.between(start, end).toMinutes() / SportType.SLOT_MINUTES;
        if (slots < sportType.getMinSlots()) {
            fail("Sân loại này phải đặt tối thiểu " + sportType.getMinSlots() * SportType.SLOT_MINUTES + " phút");
        }
        if (slots > maxSlots) {
            fail("Mỗi lượt đặt tối đa " + maxSlots * SportType.SLOT_MINUTES + " phút");
        }
    }

    private static void fail(String message) {
        throw new AppException(ErrorCode.INVALID_BOOKING_TIME, message);
    }
}
