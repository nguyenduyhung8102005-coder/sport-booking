package com.sportbooking.repository;

import com.sportbooking.entity.BookingSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, Long> {

    /** Các slot đã bị chiếm của 1 sân trong 1 ngày (để vẽ lịch trống). Chỉ lấy giờ, không load entity. */
    @Query("SELECT s.slotStart FROM BookingSlot s WHERE s.court.id = :courtId AND s.slotDate = :date")
    List<LocalTime> findBookedSlotStarts(@Param("courtId") Long courtId, @Param("date") LocalDate date);

    /** Trong các slot khách muốn đặt, slot nào đã có người giữ. */
    @Query("""
            SELECT s.slotStart FROM BookingSlot s
            WHERE s.court.id = :courtId AND s.slotDate = :date AND s.slotStart IN :slotStarts
            ORDER BY s.slotStart
            """)
    List<LocalTime> findTakenSlotStarts(@Param("courtId") Long courtId,
                                        @Param("date") LocalDate date,
                                        @Param("slotStarts") Collection<LocalTime> slotStarts);

    long countByCourtIdAndSlotDate(Long courtId, LocalDate slotDate);

    long countByBookingId(Long bookingId);

    /** Nhả toàn bộ slot của 1 booking: 1 câu DELETE chạy ngay (dùng index idx_booking_slots_booking). */
    @Modifying
    @Query("DELETE FROM BookingSlot s WHERE s.booking.id = :bookingId")
    int deleteByBookingId(@Param("bookingId") Long bookingId);
}
