package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Đánh giá sau khi chơi. Mỗi lượt đặt tối đa 1 đánh giá (UNIQUE uk_reviews_booking).
 * venue_id lưu trùng (có thể suy ra từ booking -> court -> venue) để liệt kê đánh giá
 * theo cụm sân không cần JOIN 2 bảng.
 */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    /** short vì cột là SMALLINT; dùng int thì Hibernate validate báo lệch kiểu. */
    @Column(nullable = false)
    private short rating;

    private String comment;

    public Review(Booking booking, User customer, Venue venue, int rating, String comment) {
        this.booking = booking;
        this.customer = customer;
        this.venue = venue;
        this.rating = (short) rating;
        this.comment = comment;
    }
}
