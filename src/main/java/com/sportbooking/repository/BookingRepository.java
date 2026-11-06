package com.sportbooking.repository;

import com.sportbooking.entity.Booking;
import com.sportbooking.entity.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * @EntityGraph "court", "court.venue": load luôn sân và cụm sân trong cùng câu query,
 * vì response cần tên sân, tên cụm sân. Không có nó -> lỗi N+1 khi xem danh sách lượt đặt.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"court", "court.venue"})
    Page<Booking> findByCustomerId(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"court", "court.venue"})
    Page<Booking> findByCustomerIdAndStatus(Long customerId, BookingStatus status, Pageable pageable);

    /** Chỉ tìm thấy khi booking thuộc về customerId (không xem được booking của người khác). */
    @EntityGraph(attributePaths = {"court", "court.venue"})
    Optional<Booking> findByIdAndCustomerId(Long id, Long customerId);

    /** Số lượt đặt đang giữ chỗ (PENDING và chưa quá hạn) của 1 khách. */
    long countByCustomerIdAndStatusAndExpiresAtAfter(Long customerId, BookingStatus status, Instant time);

    long countByCourtId(Long courtId);
}
