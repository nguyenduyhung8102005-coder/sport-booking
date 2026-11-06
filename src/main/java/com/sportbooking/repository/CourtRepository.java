package com.sportbooking.repository;

import com.sportbooking.entity.Court;
import com.sportbooking.entity.enums.CourtStatus;
import com.sportbooking.repository.projection.VenueSportStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourtRepository extends JpaRepository<Court, Long> {

    List<Court> findByVenueIdOrderByNameAsc(Long venueId);

    boolean existsByVenueIdAndName(Long venueId, String name);

    /**
     * Sân con thuộc cụm sân của ownerId. JOIN FETCH: lấy luôn Venue trong cùng câu query
     * (service cần giờ mở cửa của venue để kiểm tra bảng giá).
     */
    @Query("""
            SELECT c FROM Court c
            JOIN FETCH c.venue v
            WHERE c.id = :courtId AND v.owner.id = :ownerId
            """)
    Optional<Court> findOwnedCourt(@Param("courtId") Long courtId, @Param("ownerId") Long ownerId);

    /**
     * Sân kèm cụm sân và chủ sân (để kiểm tra sân có đang mở cho khách đặt không) trong 1 câu query.
     */
    @Query("""
            SELECT c FROM Court c
            JOIN FETCH c.venue v
            JOIN FETCH v.owner
            WHERE c.id = :courtId
            """)
    Optional<Court> findWithVenueAndOwnerById(@Param("courtId") Long courtId);

    /**
     * Thống kê theo (cụm sân, loại sân) cho CẢ TRANG kết quả tìm kiếm chỉ bằng 1 câu query:
     * số sân, giá thấp nhất, giá cao nhất.
     * Không có câu này, mỗi cụm sân trong trang sẽ cần thêm 1-2 query riêng (lỗi N+1).
     *
     * "LEFT JOIN PriceRule p ON p.court = c": join tới entity không có quan hệ khai báo từ phía Court.
     * LEFT để sân chưa có bảng giá vẫn được đếm (giá = null).
     */
    @Query("""
            SELECT new com.sportbooking.repository.projection.VenueSportStat(
                c.venue.id, c.sportType, COUNT(DISTINCT c.id), MIN(p.pricePerHour), MAX(p.pricePerHour))
            FROM Court c
            LEFT JOIN PriceRule p ON p.court = c
            WHERE c.venue.id IN :venueIds AND c.status = :status
            GROUP BY c.venue.id, c.sportType
            ORDER BY c.venue.id, c.sportType
            """)
    List<VenueSportStat> findSportStats(@Param("venueIds") Collection<Long> venueIds,
                                        @Param("status") CourtStatus status);
}
