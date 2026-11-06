package com.sportbooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Tách @EnableJpaAuditing khỏi class main để các test slice như @WebMvcTest
 * (không load JPA) không bị lỗi.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
