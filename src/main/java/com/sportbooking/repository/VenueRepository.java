package com.sportbooking.repository;

import com.sportbooking.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long>, JpaSpecificationExecutor<Venue> {

    /**
     * Tìm cụm sân theo id NHƯNG chỉ khi thuộc về ownerId.
     * Spring Data tự hiểu "OwnerId" là owner.id -> WHERE v.id = ? AND v.owner_id = ?
     */
    Optional<Venue> findByIdAndOwnerId(Long id, Long ownerId);

    Page<Venue> findByOwnerId(Long ownerId, Pageable pageable);

    /**
     * Cộng thêm 1 đánh giá vào cụm sân bằng MỘT câu UPDATE NGUYÊN TỬ.
     *
     * Cách SAI (lost update): đọc reviewCount = 10 -> tính 11 -> ghi 11. Hai người đánh giá cùng lúc
     * đều đọc 10 và đều ghi 11 -> mất 1 lượt. Kể cả "UPDATE ... SET review_count = (SELECT COUNT(*) ...)"
     * cũng mất, vì câu SELECT con không thấy review chưa commit của người kia.
     *
     * Cách ĐÚNG: "review_count = review_count + 1". PostgreSQL khóa dòng venue khi UPDATE;
     * người đến sau phải đợi, rồi cộng tiếp trên giá trị MỚI NHẤT. Biểu thức bên phải dấu = luôn dùng
     * giá trị cũ của chính dòng đó, nên avg_rating tính từ (rating_total + :rating) / (review_count + 1).
     *
     * SQL thuần (nativeQuery) vì cần ROUND và CAST sang numeric của PostgreSQL.
     */
    @Modifying
    @Query(value = """
            UPDATE venues
            SET review_count = review_count + 1,
                rating_total = rating_total + :rating,
                avg_rating   = ROUND(CAST(rating_total + :rating AS numeric) / (review_count + 1), 1),
                updated_at   = now()
            WHERE id = :venueId
            """, nativeQuery = true)
    int addRating(@Param("venueId") Long venueId, @Param("rating") int rating);
}
