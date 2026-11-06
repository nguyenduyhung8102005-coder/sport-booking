package com.sportbooking.integration.vnpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình VNPay (app.vnpay.*). Không đặt @NotBlank cho tmnCode/hashSecret:
 * máy chưa có tài khoản sandbox vẫn chạy được app, chỉ API thanh toán báo "chưa cấu hình".
 */
@ConfigurationProperties(prefix = "app.vnpay")
public record VnPayProperties(
        String tmnCode,
        String hashSecret,
        String payUrl,
        String returnUrl,
        String version) {

    public boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
    }
}
