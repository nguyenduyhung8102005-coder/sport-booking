package com.sportbooking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật cơ chế @Scheduled. Tách thành class riêng có điều kiện để tắt được bằng cấu hình
 * (app.scheduling.enabled=false trong test).
 *
 * Mặc định Spring chạy MỌI job @Scheduled trên 1 thread duy nhất: job này chạy lâu thì job khác phải đợi.
 * Hiện chỉ có 1 job nên đủ dùng; nhiều job thì cấu hình spring.task.scheduling.pool.size.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
