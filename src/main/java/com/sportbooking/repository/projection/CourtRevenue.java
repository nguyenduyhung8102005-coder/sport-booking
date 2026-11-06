package com.sportbooking.repository.projection;

/** 1 dòng kết quả GROUP BY sân con. */
public record CourtRevenue(Long courtId, String courtName, Long bookingCount, Long revenue) {
}
