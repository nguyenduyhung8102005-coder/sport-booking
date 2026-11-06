package com.sportbooking.repository;

import com.sportbooking.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    /** Đánh giá của 1 cụm sân, kèm tên người đánh giá (EntityGraph, không N+1). */
    @EntityGraph(attributePaths = "customer")
    Page<Review> findByVenueId(Long venueId, Pageable pageable);
}
