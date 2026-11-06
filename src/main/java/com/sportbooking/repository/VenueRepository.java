package com.sportbooking.repository;

import com.sportbooking.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long>, JpaSpecificationExecutor<Venue> {

    /**
     * Tìm cụm sân theo id NHƯNG chỉ khi thuộc về ownerId.
     * Spring Data tự hiểu "OwnerId" là owner.id -> WHERE v.id = ? AND v.owner_id = ?
     */
    Optional<Venue> findByIdAndOwnerId(Long id, Long ownerId);

    Page<Venue> findByOwnerId(Long ownerId, Pageable pageable);
}
