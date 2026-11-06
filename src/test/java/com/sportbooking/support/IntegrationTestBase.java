package com.sportbooking.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Lớp cha cho integration test: khởi động toàn bộ ứng dụng Spring + PostgreSQL thật (Testcontainers),
 * Flyway chạy đủ migration. Khác unit test (mock mọi thứ), ở đây test chạy qua database thật.
 *
 * Profile "test": không chạy DevDataSeeder, giảm log SQL.
 * Mỗi test tự tạo dữ liệu riêng (sân mới, khách mới) nên các test không ảnh hưởng nhau.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class IntegrationTestBase {
}
