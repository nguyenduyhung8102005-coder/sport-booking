package com.sportbooking.service;

import com.sportbooking.common.PageResponse;
import com.sportbooking.common.PageableUtils;
import com.sportbooking.dto.request.ReviewRequest;
import com.sportbooking.dto.response.ReviewResponse;
import com.sportbooking.entity.Booking;
import com.sportbooking.entity.Review;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.ReviewRepository;
import com.sportbooking.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final VenueRepository venueRepository;

    /**
     * Thêm đánh giá và cập nhật điểm trung bình của cụm sân trong CÙNG 1 transaction:
     * review lưu được mà điểm không cập nhật được (hoặc ngược lại) thì rollback cả hai.
     */
    @Transactional
    public ReviewResponse createReview(Long customerId, Long bookingId, ReviewRequest request) {
        Booking booking = bookingRepository.findByIdAndCustomerId(bookingId, customerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lượt đặt id=" + bookingId));
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new AppException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Venue venue = booking.getCourt().getVenue();
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        Review review = new Review(booking, booking.getCustomer(), venue, request.rating(), comment);
        try {
            // Cùng kỹ thuật đã dùng nhiều lần: kiểm tra trước cho lỗi dễ hiểu, UNIQUE ở DB chặn cuối
            // (khách bấm "Gửi" 2 lần thật nhanh)
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        venueRepository.addRating(venue.getId(), request.rating());
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getVenueReviews(Long venueId, Pageable pageable) {
        venueRepository.findById(venueId)
                .filter(Venue::isPubliclyVisible)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cụm sân id=" + venueId));
        Pageable safePageable = PageableUtils.withAllowedSort(pageable, List.of("createdAt", "rating"));
        return PageResponse.from(reviewRepository.findByVenueId(venueId, safePageable).map(ReviewService::toResponse));
    }

    private static ReviewResponse toResponse(Review review) {
        return new ReviewResponse(review.getId(), review.getRating(), review.getComment(),
                review.getCustomer().getFullName(), review.getCreatedAt());
    }
}
