package com.sportbooking.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Khởi động 1 container PostgreSQL 17 THẬT (cùng phiên bản với docker-compose) cho integration test.
 *
 * @ServiceConnection: Spring Boot tự lấy URL/username/password của container để cấu hình datasource,
 * không cần khai báo spring.datasource.* cho test.
 *
 * Khai báo container là @Bean: container sống cùng Spring context. Context được cache và dùng lại
 * giữa các class test -> chỉ khởi động container 1 lần cho cả bộ test.
 *
 * Yêu cầu: Docker đang chạy trên máy.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
