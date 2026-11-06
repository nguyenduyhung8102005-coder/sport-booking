package com.sportbooking.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tiện ích cho refresh token.
 *
 * Vì sao hash refresh token bằng SHA-256 mà không dùng BCrypt như mật khẩu?
 * - Mật khẩu do người đặt -> entropy thấp, dễ đoán -> cần BCrypt (chậm, có salt) để chống brute-force.
 * - Refresh token là 256 bit ngẫu nhiên -> không thể đoán -> hash nhanh là đủ an toàn.
 * - SHA-256 cho cùng kết quả mỗi lần -> tra cứu được bằng WHERE token_hash = ?.
 *   BCrypt có salt ngẫu nhiên nên không tra cứu kiểu này được.
 */
public final class TokenUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtils() {
    }

    /** 32 byte ngẫu nhiên (256 bit), mã hóa Base64 URL-safe để gửi qua JSON/URL an toàn. */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Mọi JVM đều bắt buộc hỗ trợ SHA-256, nên nhánh này không xảy ra trong thực tế
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
