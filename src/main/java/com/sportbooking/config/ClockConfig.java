package com.sportbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Inject Clock thay vì gọi Instant.now() trực tiếp: trong test có thể "đóng băng" thời gian
 * (Clock.fixed) để kiểm tra token hết hạn, booking quá hạn... một cách chính xác.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
