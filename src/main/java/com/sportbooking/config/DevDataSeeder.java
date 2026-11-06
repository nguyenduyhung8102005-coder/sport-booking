package com.sportbooking.config;

import com.sportbooking.entity.Court;
import com.sportbooking.entity.OwnerProfile;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.User;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.entity.enums.PriceLabel;
import com.sportbooking.entity.enums.SportType;
import com.sportbooking.entity.enums.VenueStatus;
import com.sportbooking.repository.CourtRepository;
import com.sportbooking.repository.OwnerProfileRepository;
import com.sportbooking.repository.PriceRuleRepository;
import com.sportbooking.repository.UserRepository;
import com.sportbooking.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalTime;
import java.util.Locale;

/**
 * Dữ liệu mẫu để thử API ngay mà không phải tạo tay. CHỈ chạy ở profile "dev".
 *
 * @Profile("dev"): bean này chỉ được tạo khi profile dev đang bật.
 * application.yml đặt spring.profiles.default=dev, nên chạy trên máy là có dữ liệu mẫu.
 * Khi deploy, đặt SPRING_PROFILES_ACTIVE=prod -> seeder không chạy.
 *
 * Vì sao không seed bằng Flyway? Migration chạy ở MỌI môi trường, kể cả production.
 */
@Slf4j
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    static final String OWNER_EMAIL = "owner@sportbooking.vn";
    static final String CUSTOMER_EMAIL = "customer@sportbooking.vn";
    private static final String DEMO_PASSWORD = "Demo@123456";

    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(OWNER_EMAIL)) {
            return;   // đã seed ở lần chạy trước
        }
        User admin = userRepository.findByEmail(adminProperties.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (admin == null) {
            log.warn("Admin account not found, skip dev data seeding");
            return;
        }

        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);
        userRepository.save(User.newCustomer(CUSTOMER_EMAIL, passwordHash, "Nguyễn Khách Hàng", "0912345678"));

        User owner = userRepository.save(User.newCustomer(OWNER_EMAIL, passwordHash, "Trần Chủ Sân", "0987654321"));
        OwnerProfile profile = ownerProfileRepository.save(new OwnerProfile(owner, "Công ty TNHH Thể thao Demo"));
        profile.approve(admin, clock.instant());
        owner.promoteToOwner();

        // 1. Sân bóng lớn: 3 sân 5 người + 1 sân 7 người
        Venue myDinh = venue(owner, "Sân bóng Mỹ Đình Arena", "Số 1 Lê Đức Thọ", "Nam Từ Liêm", "06:00", "23:00");
        for (int i = 1; i <= 3; i++) {
            Court court = court(myDinh, "Sân 5-" + i, SportType.FOOTBALL_5);
            price(court, DayType.WEEKDAY, "06:00", "17:00", 300_000, PriceLabel.NORMAL);
            price(court, DayType.WEEKDAY, "17:00", "23:00", 500_000, PriceLabel.PEAK);
            price(court, DayType.WEEKEND, "06:00", "23:00", 550_000, PriceLabel.PEAK);
        }
        Court seven = court(myDinh, "Sân 7-1", SportType.FOOTBALL_7);
        price(seven, DayType.WEEKDAY, "06:00", "17:00", 600_000, PriceLabel.NORMAL);
        price(seven, DayType.WEEKDAY, "17:00", "23:00", 900_000, PriceLabel.PEAK);
        price(seven, DayType.WEEKEND, "06:00", "23:00", 1_000_000, PriceLabel.PEAK);

        // 2. Cụm hỗn hợp ở Hà Đông: 2 sân bóng + 2 sân cầu lông
        Venue haDong = venue(owner, "Cụm sân Hà Đông Sport", "Số 10 Quang Trung", "Hà Đông", "05:30", "22:00");
        for (int i = 1; i <= 2; i++) {
            Court football = court(haDong, "Sân bóng " + i, SportType.FOOTBALL_5);
            price(football, DayType.WEEKDAY, "05:30", "17:00", 250_000, PriceLabel.NORMAL);
            price(football, DayType.WEEKDAY, "17:00", "22:00", 400_000, PriceLabel.PEAK);
            price(football, DayType.WEEKEND, "05:30", "22:00", 450_000, PriceLabel.PEAK);

            Court badminton = court(haDong, "Cầu lông " + i, SportType.BADMINTON);
            price(badminton, DayType.WEEKDAY, "05:30", "16:00", 80_000, PriceLabel.NORMAL);
            price(badminton, DayType.WEEKDAY, "16:00", "22:00", 120_000, PriceLabel.PEAK);
            price(badminton, DayType.WEEKEND, "05:30", "22:00", 130_000, PriceLabel.PEAK);
        }

        // 3. Nhà thi đấu cầu lông
        Venue cauGiay = venue(owner, "Nhà thi đấu cầu lông Cầu Giấy", "Số 25 Trần Thái Tông", "Cầu Giấy", "06:00", "22:00");
        for (int i = 1; i <= 4; i++) {
            Court court = court(cauGiay, "Sân " + i, SportType.BADMINTON);
            price(court, DayType.WEEKDAY, "06:00", "17:00", 70_000, PriceLabel.NORMAL);
            price(court, DayType.WEEKDAY, "17:00", "22:00", 110_000, PriceLabel.PEAK);
            price(court, DayType.WEEKEND, "06:00", "22:00", 120_000, PriceLabel.PEAK);
        }

        // 4. Cụm đang tạm ngừng: KHÔNG được xuất hiện trong kết quả tìm kiếm
        Venue closed = venue(owner, "Sân bóng Thanh Xuân (tạm ngừng)", "Số 5 Nguyễn Trãi", "Thanh Xuân", "06:00", "22:00");
        court(closed, "Sân 1", SportType.FOOTBALL_5);
        closed.changeStatus(VenueStatus.INACTIVE);

        log.info("Seeded dev data. Demo accounts (password {}): {}, {}", DEMO_PASSWORD, OWNER_EMAIL, CUSTOMER_EMAIL);
    }

    private Venue venue(User owner, String name, String address, String district, String open, String close) {
        return venueRepository.save(new Venue(owner, name, null, address, district, "Hà Nội",
                LocalTime.parse(open), LocalTime.parse(close)));
    }

    private Court court(Venue venue, String name, SportType sportType) {
        return courtRepository.save(new Court(venue, name, sportType));
    }

    private void price(Court court, DayType dayType, String start, String end, long pricePerHour, PriceLabel label) {
        priceRuleRepository.save(new PriceRule(court, dayType, LocalTime.parse(start), LocalTime.parse(end),
                pricePerHour, label));
    }
}
