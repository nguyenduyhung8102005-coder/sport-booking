package com.sportbooking.repository;

import com.sportbooking.entity.Booking;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.repository.projection.CourtRevenue;
import com.sportbooking.repository.projection.DailyRevenue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
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

    /**
     * Id các booking PENDING đã quá hạn, cũ nhất trước. Chỉ lấy id (nhẹ), mỗi id xử lý riêng sau.
     *
     * Viết hằng số enum THẲNG vào câu query (không dùng tham số :status): Hibernate sinh ra
     * "WHERE status = 'PENDING'", khớp đúng điều kiện của partial index idx_bookings_pending_expiry.
     * Nếu status là tham số, PostgreSQL có thể không dùng được partial index đó.
     */
    @Query("""
            SELECT b.id FROM Booking b
            WHERE b.status = com.sportbooking.entity.enums.BookingStatus.PENDING
              AND b.expiresAt < :now
            ORDER BY b.expiresAt
            """)
    List<Long> findOverdueIds(@Param("now") Instant now, Pageable pageable);

    /** Lịch 1 ngày của cả cụm sân cho chủ sân: kèm sân và khách trong 1 câu query. */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.court c
            JOIN FETCH b.customer
            WHERE c.venue.id = :venueId AND b.bookingDate = :date
            ORDER BY c.name, b.startTime
            """)
    List<Booking> findVenueSchedule(@Param("venueId") Long venueId, @Param("date") LocalDate date);

    /**
     * Chuyển MỌI lượt CONFIRMED đã qua giờ chơi sang COMPLETED bằng 1 câu UPDATE (bulk update).
     * Không load từng entity: nhanh dù có hàng nghìn lượt.
     *
     * Bulk update bỏ qua cơ chế @Version và auditing của Hibernate, nên phải TỰ tăng version
     * (để request nào đang giữ bản cũ của booking sẽ gặp optimistic lock) và tự gán updatedAt.
     */
    @Modifying
    @Query("""
            UPDATE Booking b
            SET b.status = com.sportbooking.entity.enums.BookingStatus.COMPLETED,
                b.version = b.version + 1,
                b.updatedAt = :now
            WHERE b.status = com.sportbooking.entity.enums.BookingStatus.CONFIRMED
              AND (b.bookingDate < :today OR (b.bookingDate = :today AND b.endTime <= :timeOfDay))
            """)
    int completeFinishedBookings(@Param("today") LocalDate today,
                                 @Param("timeOfDay") LocalTime timeOfDay,
                                 @Param("now") Instant now);

    /** Doanh thu theo ngày của 1 cụm sân. Tính tổng ngay trong DB, không load từng booking về Java. */
    @Query("""
            SELECT new com.sportbooking.repository.projection.DailyRevenue(
                b.bookingDate, COUNT(b), SUM(b.totalPrice))
            FROM Booking b
            WHERE b.court.venue.id = :venueId
              AND b.status IN :statuses
              AND b.bookingDate BETWEEN :fromDate AND :toDate
            GROUP BY b.bookingDate
            ORDER BY b.bookingDate
            """)
    List<DailyRevenue> sumRevenueByDay(@Param("venueId") Long venueId,
                                       @Param("statuses") Collection<BookingStatus> statuses,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate);

    /** Doanh thu theo từng sân con, sân nhiều tiền nhất trước. */
    @Query("""
            SELECT new com.sportbooking.repository.projection.CourtRevenue(
                c.id, c.name, COUNT(b), SUM(b.totalPrice))
            FROM Booking b
            JOIN b.court c
            WHERE c.venue.id = :venueId
              AND b.status IN :statuses
              AND b.bookingDate BETWEEN :fromDate AND :toDate
            GROUP BY c.id, c.name
            ORDER BY SUM(b.totalPrice) DESC
            """)
    List<CourtRevenue> sumRevenueByCourt(@Param("venueId") Long venueId,
                                         @Param("statuses") Collection<BookingStatus> statuses,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate);

    /** Booking thuộc một sân của ownerId (chủ sân chỉ thao tác trên lượt đặt ở sân của mình). */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.court c
            JOIN FETCH c.venue v
            JOIN FETCH b.customer
            WHERE b.id = :bookingId AND v.owner.id = :ownerId
            """)
    Optional<Booking> findForOwner(@Param("bookingId") Long bookingId, @Param("ownerId") Long ownerId);
}
