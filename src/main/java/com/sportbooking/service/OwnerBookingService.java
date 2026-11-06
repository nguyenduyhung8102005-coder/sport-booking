package com.sportbooking.service;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.response.OwnerBookingResponse;
import com.sportbooking.entity.Booking;
import com.sportbooking.event.BookingCancelledByOwnerEvent;
import com.sportbooking.event.BookingEmailData;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.mapper.BookingMapper;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Chủ sân xem lịch đặt và hủy lượt đặt tại sân CỦA MÌNH. */
@Service
@RequiredArgsConstructor
public class OwnerBookingService {

    private final BookingRepository bookingRepository;
    private final VenueRepository venueRepository;
    private final BookingSlotReleaser bookingSlotReleaser;
    private final BookingProperties bookingProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** Lịch 1 ngày của cả cụm sân (mọi trạng thái), sắp theo sân rồi theo giờ. 1 câu query. */
    @Transactional(readOnly = true)
    public List<OwnerBookingResponse> getVenueSchedule(Long ownerId, Long venueId, LocalDate date) {
        venueRepository.findByIdAndOwnerId(venueId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cụm sân id=" + venueId));
        return bookingRepository.findVenueSchedule(venueId, date).stream()
                .map(BookingMapper::toOwnerResponse)
                .toList();
    }

    @Transactional
    public OwnerBookingResponse cancelByOwner(Long ownerId, Long bookingId, String reason) {
        Booking booking = bookingRepository.findForOwner(bookingId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lượt đặt id=" + bookingId));

        Instant nowInstant = clock.instant();
        CancellationPolicy.checkOwnerCancellation(booking.getStatus(), booking.startDateTime(),
                LocalDateTime.ofInstant(nowInstant, bookingProperties.zone()));

        booking.cancel("Chủ sân hủy: " + reason.trim(), nowInstant);
        bookingSlotReleaser.releaseSlots(booking);
        // Báo khách qua email sau khi commit. Lượt đã thanh toán: hoàn tiền thủ công (tự động là phần mở rộng).
        eventPublisher.publishEvent(new BookingCancelledByOwnerEvent(BookingEmailData.from(booking), reason.trim()));
        return BookingMapper.toOwnerResponse(booking);
    }
}
