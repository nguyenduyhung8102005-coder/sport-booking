package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
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
import java.time.LocalDateTime;
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
     * Optimistic lock: Hibernate tự sinh "UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?".
     * Nếu 0 dòng được cập nhật (người khác vừa sửa trước) -> ném OptimisticLockException.
     * Bảo vệ khi job hết hạn, khách hủy, (GĐ 6) callback thanh toán cùng sửa 1 booking.
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

    // ===================== Máy trạng thái =====================
    //
    //   PENDING ──(thanh toán, GĐ 6)──> CONFIRMED ──(chơi xong, GĐ 7)──> COMPLETED
    //      │                               │
    //      ├──(quá hạn giữ chỗ)──> EXPIRED │
    //      └──(khách / chủ sân hủy)────────┴──> CANCELLED
    //
    // Mọi thay đổi trạng thái đi qua các method dưới đây, nên không thể có chuyển đổi sai
    // (ví dụ EXPIRED -> CONFIRMED) dù gọi từ đâu.

    /** PENDING và CONFIRMED đang chiếm slot trong booking_slots; các trạng thái còn lại đã nhả slot. */
    public boolean holdsSlots() {
        return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
    }

    public boolean isOverdue(Instant now) {
        return status == BookingStatus.PENDING && expiresAt != null && expiresAt.isBefore(now);
    }

    public LocalDateTime startDateTime() {
        return LocalDateTime.of(bookingDate, startTime);
    }

    /** Thanh toán thành công (GĐ 6). Slot vẫn giữ nguyên, chỉ đổi trạng thái. */
    public void confirm() {
        if (status != BookingStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_BOOKING_STATE,
                    "Chỉ lượt đặt đang chờ thanh toán mới có thể xác nhận (hiện tại: " + status + ")");
        }
        status = BookingStatus.CONFIRMED;
    }

    public void expire() {
        if (status != BookingStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_BOOKING_STATE,
                    "Chỉ lượt đặt đang chờ thanh toán mới có thể hết hạn (hiện tại: " + status + ")");
        }
        status = BookingStatus.EXPIRED;
    }

    public void cancel(String reason, Instant now) {
        if (!holdsSlots()) {
            throw new AppException(ErrorCode.INVALID_BOOKING_STATE,
                    "Không thể hủy lượt đặt đang ở trạng thái " + status);
        }
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
        cancelReason = reason;
    }
}
