package com.sportbooking.repository;

import com.sportbooking.entity.Court;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.User;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.CourtStatus;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.SportType;
import com.sportbooking.entity.enums.UserStatus;
import com.sportbooking.entity.enums.VenueStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Các điều kiện tìm kiếm cụm sân. Mỗi method = 1 mảnh WHERE, tham số rỗng = bỏ qua.
 * Service ghép chúng bằng Specification.allOf(...) -> 1 câu SQL duy nhất.
 */
public final class VenueSpecifications {

    private VenueSpecifications() {
    }

    /** Chỉ hiện cụm sân ACTIVE của chủ sân không bị khóa. */
    public static Specification<Venue> isPubliclyVisible() {
        return (root, query, cb) -> {
            Join<Venue, User> owner = root.join("owner");
            return cb.and(
                    cb.equal(root.get("status"), VenueStatus.ACTIVE),
                    cb.equal(owner.get("status"), UserStatus.ACTIVE));
        };
    }

    public static Specification<Venue> nameContains(String keyword) {
        return (root, query, cb) -> isBlank(keyword)
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("name")), "%" + normalize(keyword) + "%");
    }

    public static Specification<Venue> cityEquals(String city) {
        return (root, query, cb) -> isBlank(city)
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("city")), normalize(city));
    }

    public static Specification<Venue> districtEquals(String district) {
        return (root, query, cb) -> isBlank(district)
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("district")), normalize(district));
    }

    /**
     * Cụm sân có ÍT NHẤT MỘT sân con thỏa TẤT CẢ điều kiện: đang hoạt động, đúng loại sân,
     * và có khung giá nằm trong khoảng giá.
     *
     * Vì sao gom vào MỘT subquery? Nếu tách thành "có sân bóng" và "có giá dưới 100k" riêng,
     * một cụm có sân bóng 500k và sân cầu lông 80k sẽ khớp sai khi khách tìm "sân bóng dưới 100k".
     *
     * SQL sinh ra có dạng:
     *   WHERE EXISTS (SELECT c.id FROM courts c
     *                 WHERE c.venue_id = v.id AND c.status = 'ACTIVE' AND c.sport_type = ?
     *                   AND EXISTS (SELECT p.id FROM price_rules p
     *                               WHERE p.court_id = c.id AND p.price_per_hour BETWEEN ? AND ?))
     */
    public static Specification<Venue> hasMatchingCourt(SportType sportType, DayType dayType,
                                                        Long minPrice, Long maxPrice) {
        return (root, query, cb) -> {
            boolean filterPrice = minPrice != null || maxPrice != null;
            if (sportType == null && !filterPrice) {
                return cb.conjunction();
            }

            Subquery<Long> courtQuery = query.subquery(Long.class);
            Root<Court> court = courtQuery.from(Court.class);
            List<Predicate> courtConditions = new ArrayList<>();
            courtConditions.add(cb.equal(court.get("venue"), root));        // liên kết với cụm sân bên ngoài
            courtConditions.add(cb.equal(court.get("status"), CourtStatus.ACTIVE));
            if (sportType != null) {
                courtConditions.add(cb.equal(court.get("sportType"), sportType));
            }

            if (filterPrice) {
                Subquery<Long> priceQuery = query.subquery(Long.class);
                Root<PriceRule> rule = priceQuery.from(PriceRule.class);
                List<Predicate> priceConditions = new ArrayList<>();
                priceConditions.add(cb.equal(rule.get("court"), court));
                if (dayType != null) {
                    priceConditions.add(cb.equal(rule.get("dayType"), dayType));
                }
                if (minPrice != null) {
                    priceConditions.add(cb.greaterThanOrEqualTo(rule.get("pricePerHour"), minPrice));
                }
                if (maxPrice != null) {
                    priceConditions.add(cb.lessThanOrEqualTo(rule.get("pricePerHour"), maxPrice));
                }
                priceQuery.select(rule.get("id")).where(priceConditions.toArray(Predicate[]::new));
                courtConditions.add(cb.exists(priceQuery));
            }

            courtQuery.select(court.get("id")).where(courtConditions.toArray(Predicate[]::new));
            return cb.exists(courtQuery);
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
