package com.sportbooking.repository;

import com.sportbooking.entity.OwnerProfile;
import com.sportbooking.entity.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @EntityGraph(attributePaths = "user"): load luôn User bằng JOIN trong cùng 1 câu query.
 * Không có nó, khi map 20 hồ sơ sang DTO sẽ phát sinh thêm 20 câu SELECT users (lỗi N+1).
 */
public interface OwnerProfileRepository extends JpaRepository<OwnerProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Page<OwnerProfile> findByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<OwnerProfile> findAll(Pageable pageable);
}
