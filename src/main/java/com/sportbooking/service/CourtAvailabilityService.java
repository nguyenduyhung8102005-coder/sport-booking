package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.response.CourtAvailabilityResponse;
import com.sportbooking.dto.response.CourtAvailabilityResponse.Slot;
import com.sportbooking.dto.response.CourtAvailabilityResponse.SlotStatus;
import com.sportbooking.entity.Court;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.CourtStatus;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.SportType;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.repository.BookingSlotRepository;
import com.sportbooking.repository.CourtRepository;
import com.sportbooking.repository.PriceRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Xem lịch trống của 1 sân trong 1 ngày (public, không cần đăng nhập). */
@Service
@RequiredArgsConstructor
public class CourtAvailabilityService {

    private final CourtRepository courtRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    /**
     * 3 câu query cho cả ngày: sân (+ cụm sân + chủ sân), bảng giá, các slot đã bị chiếm.
     * Phần ghép trạng thái từng slot làm trong Java.
     */
    @Transactional(readOnly = true)
    public CourtAvailabilityResponse getAvailability(Long courtId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(bookingProperties.zone()));
        LocalDate today = now.toLocalDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(bookingProperties.maxAdvanceDays()))) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TIME,
                    "Chỉ xem được lịch từ hôm nay đến " + bookingProperties.maxAdvanceDays() + " ngày tới");
        }

        Court court = findBookableCourt(courtId);
        Venue venue = court.getVenue();
        DayType dayType = DayType.of(date);
        List<PriceRule> rules = priceRuleRepository.findByCourtIdOrderByDayTypeAscStartTimeAsc(courtId);
        Set<LocalTime> booked = new HashSet<>(bookingSlotRepository.findBookedSlotStarts(courtId, date));

        List<Slot> slots = BookingSlotCalculator.slotStarts(venue.getOpenTime(), venue.getCloseTime()).stream()
                .map(start -> {
                    Long price = BookingSlotCalculator.findSlotPrice(start, dayType, rules);
                    SlotStatus status;
                    if (!LocalDateTime.of(date, start).isAfter(now)) {
                        status = SlotStatus.PAST;
                    } else if (booked.contains(start)) {
                        status = SlotStatus.BOOKED;
                    } else if (price == null) {
                        status = SlotStatus.UNAVAILABLE;
                    } else {
                        status = SlotStatus.AVAILABLE;
                    }
                    return new Slot(start, start.plusMinutes(SportType.SLOT_MINUTES), price, status);
                })
                .toList();

        return new CourtAvailabilityResponse(court.getId(), court.getName(), court.getSportType(),
                court.getSportType().getMinSlots(), date, dayType, slots);
    }

    /**
     * Sân tồn tại -> kiểm tra có đang nhận đặt không. Dùng chung với BookingService.
     * Sân không tồn tại: 404. Sân bảo trì / cụm sân ngừng hoạt động / chủ sân bị khóa: 409.
     */
    @Transactional(readOnly = true)
    public Court findBookableCourt(Long courtId) {
        Court court = courtRepository.findWithVenueAndOwnerById(courtId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sân id=" + courtId));
        if (court.getStatus() != CourtStatus.ACTIVE || !court.getVenue().isPubliclyVisible()) {
            throw new AppException(ErrorCode.COURT_NOT_AVAILABLE);
        }
        return court;
    }
}
