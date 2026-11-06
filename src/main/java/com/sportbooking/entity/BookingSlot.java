package com.sportbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * "Sổ giữ chỗ": mỗi dòng = 1 slot 30 phút của 1 sân đang bị chiếm.
 * UNIQUE (court_id, slot_date, slot_start) ở DB đảm bảo 1 slot chỉ thuộc về 1 booking.
 * Không kế thừa AuditableEntity vì bảng không có cột created_at/updated_at.
 */
@Entity
@Table(name = "booking_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_start", nullable = false)
    private LocalTime slotStart;

    public BookingSlot(Booking booking, Court court, LocalDate slotDate, LocalTime slotStart) {
        this.booking = booking;
        this.court = court;
        this.slotDate = slotDate;
        this.slotStart = slotStart;
    }
}
