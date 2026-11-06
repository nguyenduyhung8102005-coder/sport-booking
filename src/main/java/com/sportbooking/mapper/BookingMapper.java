package com.sportbooking.mapper;

import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.entity.Booking;
import com.sportbooking.entity.Court;
import com.sportbooking.entity.Venue;

public final class BookingMapper {

    private BookingMapper() {
    }

    /** Cần booking.court và court.venue đã được load (EntityGraph / JOIN FETCH). */
    public static BookingResponse toResponse(Booking booking) {
        Court court = booking.getCourt();
        Venue venue = court.getVenue();
        return new BookingResponse(
                booking.getId(),
                booking.getBookingCode(),
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                court.getId(),
                court.getName(),
                court.getSportType(),
                booking.getBookingDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getTotalPrice(),
                booking.getStatus(),
                booking.getExpiresAt(),
                booking.getCreatedAt());
    }
}
