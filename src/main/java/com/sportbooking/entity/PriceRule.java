package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.PriceLabel;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * Một dòng trong bảng giá: "Ngày thường, 17:00–22:00, 500.000đ/giờ, giờ cao điểm".
 * Khoảng thời gian là [startTime, endTime): gồm startTime, không gồm endTime.
 */
@Entity
@Table(name = "price_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceRule extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private DayType dayType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "price_per_hour", nullable = false)
    private long pricePerHour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceLabel label;

    public PriceRule(Court court, DayType dayType, LocalTime startTime, LocalTime endTime,
                     long pricePerHour, PriceLabel label) {
        this.court = court;
        this.dayType = dayType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.pricePerHour = pricePerHour;
        this.label = label;
    }

    /** Slot 30 phút bắt đầu lúc slotStart có thuộc khung giá này không (dùng ở GĐ 4). */
    public boolean covers(DayType day, LocalTime slotStart) {
        return dayType == day && !slotStart.isBefore(startTime) && slotStart.isBefore(endTime);
    }

    /** Giá 1 slot 30 phút. Luôn là số nguyên vì pricePerHour là bội số 1.000đ. */
    public long pricePerSlot() {
        return pricePerHour / 2;
    }
}
