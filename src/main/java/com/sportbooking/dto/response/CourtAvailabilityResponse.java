package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.SportType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Lịch 1 ngày của 1 sân, chia theo slot 30 phút. */
public record CourtAvailabilityResponse(
        Long courtId,
        String courtName,
        SportType sportType,
        int minSlots,             // số slot tối thiểu mỗi lượt đặt (bóng đá 2, cầu lông 1)
        LocalDate date,
        DayType dayType,
        List<Slot> slots) {

    public record Slot(
            LocalTime startTime,
            LocalTime endTime,
            Long price,           // giá slot 30 phút; null nếu chưa có giá
            SlotStatus status) {
    }

    public enum SlotStatus {
        AVAILABLE,     // đặt được
        BOOKED,        // đã có người giữ chỗ / đã đặt
        PAST,          // đã qua giờ
        UNAVAILABLE    // chủ sân chưa cấu hình giá
    }
}
