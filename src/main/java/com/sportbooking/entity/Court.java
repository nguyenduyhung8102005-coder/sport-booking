package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import com.sportbooking.entity.enums.CourtStatus;
import com.sportbooking.entity.enums.SportType;
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

/**
 * Sân con trong một cụm sân. sportType không cho đổi sau khi tạo
 * (đổi loại sân sẽ làm sai thời lượng tối thiểu và bảng giá đã cấu hình).
 */
@Entity
@Table(name = "courts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Court extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false)
    private SportType sportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourtStatus status;

    public Court(Venue venue, String name, SportType sportType) {
        this.venue = venue;
        this.name = name;
        this.sportType = sportType;
        this.status = CourtStatus.ACTIVE;
    }

    public void update(String name, CourtStatus status) {
        this.name = name;
        this.status = status;
    }
}
