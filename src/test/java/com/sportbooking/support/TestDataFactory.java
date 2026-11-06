package com.sportbooking.support;

import com.sportbooking.entity.Court;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.User;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.PriceLabel;
import com.sportbooking.entity.enums.SportType;
import com.sportbooking.repository.CourtRepository;
import com.sportbooking.repository.PriceRuleRepository;
import com.sportbooking.repository.UserRepository;
import com.sportbooking.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Tạo dữ liệu cho integration test. Mỗi lần gọi tạo dữ liệu MỚI với tên/email ngẫu nhiên,
 * nên các test dùng chung database mà không giẫm chân nhau.
 *
 * Trả về id (record TestCourt) thay vì entity: entity ra khỏi transaction thì không đọc được
 * quan hệ LAZY (LazyInitializationException).
 */
@Component
@RequiredArgsConstructor
public class TestDataFactory {

    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final PriceRuleRepository priceRuleRepository;

    public record TestCourt(Long ownerId, Long venueId, String venueName, Long courtId) {
    }

    public User customer() {
        return userRepository.save(User.newCustomer(uniqueEmail("customer"), "not-used", "Test Customer", null));
    }

    public List<User> customers(int count) {
        return userRepository.saveAll(IntStream.range(0, count)
                .mapToObj(i -> User.newCustomer(uniqueEmail("customer"), "not-used", "Test Customer " + i, null))
                .toList());
    }

    /**
     * Cụm sân mở 06:00–23:00 với 1 sân bóng 5 người.
     * Giá: ngày thường 06–17h 300k, 17–23h 500k; cuối tuần cả ngày 600k (VND/giờ).
     */
    public TestCourt footballCourt() {
        User owner = User.newCustomer(uniqueEmail("owner"), "not-used", "Test Owner", null);
        owner.promoteToOwner();
        userRepository.save(owner);

        String venueName = "Test Venue " + UUID.randomUUID();
        Venue venue = venueRepository.save(new Venue(owner, venueName, null, "1 Test Street", "Hà Đông", "Hà Nội",
                LocalTime.of(6, 0), LocalTime.of(23, 0)));
        Court court = courtRepository.save(new Court(venue, "Sân 1", SportType.FOOTBALL_5));

        priceRuleRepository.saveAll(List.of(
                new PriceRule(court, DayType.WEEKDAY, LocalTime.of(6, 0), LocalTime.of(17, 0), 300_000, PriceLabel.NORMAL),
                new PriceRule(court, DayType.WEEKDAY, LocalTime.of(17, 0), LocalTime.of(23, 0), 500_000, PriceLabel.PEAK),
                new PriceRule(court, DayType.WEEKEND, LocalTime.of(6, 0), LocalTime.of(23, 0), 600_000, PriceLabel.PEAK)));

        return new TestCourt(owner.getId(), venue.getId(), venueName, court.getId());
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.vn";
    }
}
