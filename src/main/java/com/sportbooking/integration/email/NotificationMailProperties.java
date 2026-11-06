package com.sportbooking.integration.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.mail.* (đặt tên khác MailProperties của Spring Boot để không nhầm với spring.mail.*). */
@ConfigurationProperties(prefix = "app.mail")
public record NotificationMailProperties(boolean enabled, String from) {
}
