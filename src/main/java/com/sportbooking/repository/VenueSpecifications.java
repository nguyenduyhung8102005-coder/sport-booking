package com.sportbooking.repository;

import com.sportbooking.entity.BookingSlot;
import com.sportbooking.entity.Court;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.User;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.CourtStatus;
import com.sportbooking.entity.enums.UserStatus;
import com.sportbooking.entity.enums.VenueStatus;
import com.sportbooking.repository.criteria.CourtCriteria;
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
     * có khung giá nằm trong khoảng giá, và (GĐ 4) còn trống toàn bộ khoảng giờ khách muốn.
     *
     * Vì sao gom vào MỘT subquery? Nếu tách thành "có sân bóng" và "có giá dưới 100k" riêng,
     * một cụm có sân bóng 500k và sân cầu lông 80k sẽ khớp sai khi khách tìm "sân bóng dưới 100k".
     * Tương tự: "còn trống 17:00–18:00" phải đúng với CHÍNH sân con đúng loại đó.
     *
     * SQL sinh ra có dạng:
     *   WHERE v.open_time <= ? AND v.close_time >= ?
     *     AND EXISTS (SELECT c.id FROM courts c
     *                 WHERE c.venue_id = v.id AND c.status = 'ACTIVE' AND c.sport_type = ?
     *                   AND EXISTS (SELECT p.id FROM price_rules p
     *                               WHERE p.court_id = c.id AND p.price_per_hour BETWEEN ? AND ?)
     *                   AND NOT EXISTS (SELECT s.id FROM booking_slots s
     *                                   WHERE s.court_id = c.id AND s.slot_date = ?
     *                                     AND s.slot_start >= ? AND s.slot_start < ?))
     */
    public static Specification<Venue> hasMatchingCourt(CourtCriteria criteria) {
        return (root, query, cb) -> {
            if (criteria.isEmpty()) {
                return cb.conjunction();
            }

            Subquery<Long> courtQuery = query.subquery(Long.class);
            Root<Court> court = courtQuery.from(Court.class);
            List<Predicate> courtConditions = new ArrayList<>();
            courtConditions.add(cb.equal(court.get("venue"), root));        // liên kết với cụm sân bên ngoài
            courtConditions.add(cb.equal(court.get("status"), CourtStatus.ACTIVE));
            if (criteria.sportType() != null) {
                courtConditions.add(cb.equal(court.get("sportType"), criteria.sportType()));
            }

            if (criteria.hasPriceFilter()) {
                Subquery<Long> priceQuery = query.subquery(Long.class);
                Root<PriceRule> rule = priceQuery.from(PriceRule.class);
                List<Predicate> priceConditions = new ArrayList<>();
                priceConditions.add(cb.equal(rule.get("court"), court));
                if (criteria.dayType() != null) {
                    priceConditions.add(cb.equal(rule.get("dayType"), criteria.dayType()));
                }
                if (criteria.minPrice() != null) {
                    priceConditions.add(cb.greaterThanOrEqualTo(rule.get("pricePerHour"), criteria.minPrice()));
                }
                if (criteria.maxPrice() != null) {
                    priceConditions.add(cb.lessThanOrEqualTo(rule.get("pricePerHour"), criteria.maxPrice()));
                }
                priceQuery.select(rule.get("id")).where(priceConditions.toArray(Predicate[]::new));
                courtConditions.add(cb.exists(priceQuery));
            }

            List<Predicate> venueConditions = new ArrayList<>();
            if (criteria.hasTimeRange()) {
                // Cụm sân phải mở cửa trong cả khoảng giờ
                venueConditions.add(cb.lessThanOrEqualTo(root.get("openTime"), criteria.startTime()));
                venueConditions.add(cb.greaterThanOrEqualTo(root.get("closeTime"), criteria.endTime()));

                // Sân con không có slot nào bị chiếm trong [startTime, endTime)
                Subquery<Long> busyQuery = query.subquery(Long.class);
                Root<BookingSlot> slot = busyQuery.from(BookingSlot.class);
                busyQuery.select(slot.get("id")).where(
                        cb.equal(slot.get("court"), court),
                        cb.equal(slot.get("slotDate"), criteria.date()),
                        cb.greaterThanOrEqualTo(slot.get("slotStart"), criteria.startTime()),
                        cb.lessThan(slot.get("slotStart"), criteria.endTime()));
                courtConditions.add(cb.not(cb.exists(busyQuery)));
            }

            courtQuery.select(court.get("id")).where(courtConditions.toArray(Predicate[]::new));
            venueConditions.add(cb.exists(courtQuery));
            return cb.and(venueConditions.toArray(Predicate[]::new));
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
