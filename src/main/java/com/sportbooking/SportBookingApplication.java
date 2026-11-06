package com.sportbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Loại bỏ UserDetailsServiceAutoConfiguration: ta tự xác thực bằng JWT filter,
 * không cần user mặc định "user / generated password" mà Spring Boot tạo sẵn.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SportBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SportBookingApplication.class, args);
    }
}
