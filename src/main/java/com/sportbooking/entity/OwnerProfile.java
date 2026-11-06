package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import com.sportbooking.entity.enums.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Hồ sơ xin làm chủ sân. Quan hệ 1-1 với User, dùng chung khóa chính (user_id).
 * @MapsId: giá trị của userId được lấy từ user.id, không tự sinh.
 */
@Entity
@Table(name = "owner_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnerProfile extends AuditableEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    public OwnerProfile(User user, String businessName) {
        this.user = user;
        this.businessName = businessName;
        this.approvalStatus = ApprovalStatus.PENDING;
    }

    public boolean isPending() {
        return approvalStatus == ApprovalStatus.PENDING;
    }

    /** Gửi lại sau khi bị từ chối. */
    public void resubmit(String businessName) {
        this.businessName = businessName;
        this.approvalStatus = ApprovalStatus.PENDING;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.rejectReason = null;
    }

    public void approve(User admin, Instant now) {
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.reviewedBy = admin;
        this.reviewedAt = now;
        this.rejectReason = null;
    }

    public void reject(User admin, String reason, Instant now) {
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.reviewedBy = admin;
        this.reviewedAt = now;
        this.rejectReason = reason;
    }
}
