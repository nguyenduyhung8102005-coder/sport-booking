package com.sportbooking.repository;

import com.sportbooking.entity.PriceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PriceRuleRepository extends JpaRepository<PriceRule, Long> {

    /** Lấy bảng giá của NHIỀU sân trong 1 câu query: WHERE court_id IN (...). */
    List<PriceRule> findByCourtIdInOrderByDayTypeAscStartTimeAsc(Collection<Long> courtIds);

    List<PriceRule> findByCourtIdOrderByDayTypeAscStartTimeAsc(Long courtId);

    /**
     * Xóa bằng 1 câu DELETE chạy NGAY LẬP TỨC.
     * Nếu dùng deleteAll(list), Hibernate xóa từng dòng và dồn lệnh DELETE xuống cuối lúc flush.
     */
    @Modifying
    @Query("DELETE FROM PriceRule p WHERE p.court.id = :courtId")
    int deleteByCourtId(@Param("courtId") Long courtId);
}
