package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import com.sportbooking.entity.enums.VenueStatus;
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

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Cụm sân. Không khai báo @OneToMany courts: muốn lấy sân con thì hỏi CourtRepository.
 * Quan hệ 1 chiều đơn giản hơn, tránh vô tình load cả danh sách sân mỗi khi đọc Venue.
 */
@Entity
@Table(name = "venues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String city;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VenueStatus status;

    // NUMERIC(2,1) -> BigDecimal: kiểu thập phân chính xác. Cập nhật cùng lúc với reviewCount, ratingTotal.
    @Column(name = "avg_rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal avgRating;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    /** Tổng điểm mọi đánh giá (V4). Chỉ được cập nhật bằng UPDATE nguyên tử trong VenueRepository.addRating. */
    @Column(name = "rating_total", nullable = false)
    private long ratingTotal;

    public Venue(User owner, String name, String description, String address,
                 String district, String city, LocalTime openTime, LocalTime closeTime) {
        this.owner = owner;
        updateInfo(name, description, address, district, city, openTime, closeTime);
        this.status = VenueStatus.ACTIVE;
        // Hibernate INSERT mọi cột, kể cả cột có DEFAULT trong DB -> phải tự gán giá trị ban đầu
        this.avgRating = BigDecimal.ZERO;
        this.reviewCount = 0;
        this.ratingTotal = 0;
    }

    public void updateInfo(String name, String description, String address,
                           String district, String city, LocalTime openTime, LocalTime closeTime) {
        this.name = name;
        this.description = description;
        this.address = address;
        this.district = district;
        this.city = city;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public void changeStatus(VenueStatus status) {
        this.status = status;
    }

    /** Khách chỉ thấy cụm sân đang hoạt động và chủ sân không bị khóa. Phải gọi trong transaction (owner là LAZY). */
    public boolean isPubliclyVisible() {
        return status == VenueStatus.ACTIVE && !owner.isLocked();
    }
}
