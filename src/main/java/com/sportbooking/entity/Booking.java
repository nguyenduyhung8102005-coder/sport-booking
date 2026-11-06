package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import com.sportbooking.entity.enums.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một lượt đặt sân. Giữ lịch sử đầy đủ (kể cả khi đã hủy).
 * Việc "chiếm giữ" khung giờ nằm ở bảng booking_slots, không phải ở đây.
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Chốt giá tại thời điểm đặt: chủ sân đổi bảng giá sau đó cũng không ảnh hưởng. */
    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    /**
     * Optimistic lock: Hibernate tự thêm "WHERE version = ?" vào mỗi câu UPDATE và tăng version.
     * Sẽ dùng ở GĐ 5 khi job hủy booking quá hạn và callback thanh toán cùng sửa 1 booking.
     */
    @Version
    private long version;

    public static Booking newPending(String bookingCode, User customer, Court court, LocalDate date,
                                     LocalTime startTime, LocalTime endTime, long totalPrice, Instant expiresAt) {
        Booking booking = new Booking();
        booking.bookingCode = bookingCode;
        booking.customer = customer;
        booking.court = court;
        booking.bookingDate = date;
        booking.startTime = startTime;
        booking.endTime = endTime;
        booking.totalPrice = totalPrice;
        booking.status = BookingStatus.PENDING;
        booking.expiresAt = expiresAt;
        return booking;
    }
}
