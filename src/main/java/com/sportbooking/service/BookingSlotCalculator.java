package com.sportbooking.service;

import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.SportType;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tách khoảng thời gian thành slot 30 phút và tính giá. Java thuần, không Spring, không DB.
 */
public final class BookingSlotCalculator {

    private BookingSlotCalculator() {
    }

    /** [17:00, 18:30) -> [17:00, 17:30, 18:00]. Giả định start, end đã tròn mốc :00/:30 và end > start. */
    public static List<LocalTime> slotStarts(LocalTime start, LocalTime end) {
        // Đếm số slot bằng Duration thay vì lặp "t.isBefore(end)": LocalTime 23:30 + 30 phút = 00:00
        // (quay vòng qua nửa đêm), vòng lặp kiểu đó dễ chạy sai.
        long count = Duration.between(start, end).toMinutes() / SportType.SLOT_MINUTES;
        List<LocalTime> slots = new ArrayList<>((int) count);
        for (int i = 0; i < count; i++) {
            slots.add(start.plusMinutes((long) i * SportType.SLOT_MINUTES));
        }
        return slots;
    }

    /** Giá 1 slot 30 phút, hoặc null nếu chủ sân chưa cấu hình giá cho khung đó. */
    public static Long findSlotPrice(LocalTime slotStart, DayType dayType, List<PriceRule> rules) {
        return rules.stream()
                .filter(rule -> rule.covers(dayType, slotStart))
                .findFirst()
                .map(PriceRule::pricePerSlot)
                .orElse(null);
    }

    /**
     * Tổng tiền = cộng giá từng slot. Nhờ vậy lượt đặt 16:30–17:30 vắt qua 2 khung giá
     * (300k trước 17:00, 500k sau 17:00) được tính đúng: 150k + 250k = 400k.
     */
    public static long totalPrice(List<LocalTime> slotStarts, DayType dayType, List<PriceRule> rules) {
        long total = 0;
        for (LocalTime slot : slotStarts) {
            Long price = findSlotPrice(slot, dayType, rules);
            if (price == null) {
                throw new AppException(ErrorCode.SLOT_NOT_PRICED,
                        "Khung " + slot + "–" + slot.plusMinutes(SportType.SLOT_MINUTES) + " chưa có giá, chưa thể đặt");
            }
            total += price;
        }
        return total;
    }
}
