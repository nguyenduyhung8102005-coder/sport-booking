package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * CONFIRMED -> COMPLETED khi đã qua giờ kết thúc. COMPLETED là điều kiện để khách được đánh giá.
 * Slot của lượt COMPLETED được GIỮ LẠI trong booking_slots (là quá khứ, không ai đặt lại được nữa).
 */
@Service
@RequiredArgsConstructor
public class BookingCompletionService {

    private final BookingRepository bookingRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    @Transactional
    public int completeFinishedBookings() {
        Instant now = clock.instant();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, bookingProperties.zone());
        return bookingRepository.completeFinishedBookings(localNow.toLocalDate(), localNow.toLocalTime(), now);
    }
}
