package com.sportbooking.config;

import com.sportbooking.entity.User;
import com.sportbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Tạo tài khoản ADMIN đầu tiên khi app khởi động (nếu chưa có).
 *
 * Vì sao không seed admin bằng file Flyway SQL?
 * - Phải ghi sẵn BCrypt hash của mật khẩu vào file -> mật khẩu bị "đóng cứng" và lên Git.
 * - Cách này đọc email/mật khẩu từ biến môi trường, mỗi môi trường đặt một giá trị khác nhau.
 */
@Slf4j
@Component
@Order(1)   // chạy trước DevDataSeeder (cần admin để duyệt chủ sân mẫu)
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String email = adminProperties.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.newAdmin(email, passwordEncoder.encode(adminProperties.password()), "Administrator"));
        log.info("Created default admin account: {}", email);
    }
}
