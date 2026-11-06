package com.sportbooking.security;

import com.sportbooking.config.JwtProperties;
import com.sportbooking.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test JwtService với thư viện jjwt thật (không mock), chỉ "đóng băng" thời gian bằng Clock.fixed.
 */
class JwtServiceTest {

    // 2 khóa 256 bit khác nhau, chỉ dùng cho test
    private static final String SECRET = "MDc09+Q1ZZXRUDffNEfGOyVP/aYG2ZYzsxV2kXSLX/g=";
    private static final String OTHER_SECRET = "c3BvcnRib29raW5nLXRlc3Qtc2VjcmV0LTMyLWJ5dGU=";

    private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");

    private static JwtService serviceAt(Instant time, String secret) {
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7));
        return new JwtService(properties, Clock.fixed(time, ZoneOffset.UTC));
    }

    private static User userWithId(Long id) {
        User user = User.newCustomer("an@gmail.com", "hash", "An", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void generatedToken_canBeParsedBack_toUserId() {
        String token = serviceAt(NOW, SECRET).generateAccessToken(userWithId(42L));

        Long userId = serviceAt(NOW.plus(Duration.ofMinutes(5)), SECRET).parseUserId(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void tokenUsedAfterTtl_throwsExpired() {
        String token = serviceAt(NOW, SECRET).generateAccessToken(userWithId(42L));
        JwtService sixteenMinutesLater = serviceAt(NOW.plus(Duration.ofMinutes(16)), SECRET);

        assertThatThrownBy(() -> sixteenMinutesLater.parseUserId(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        String forgedToken = serviceAt(NOW, OTHER_SECRET).generateAccessToken(userWithId(42L));

        assertThatThrownBy(() -> serviceAt(NOW, SECRET).parseUserId(forgedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayload_isRejected() {
        String token = serviceAt(NOW, SECRET).generateAccessToken(userWithId(42L));
        String[] parts = token.split("\\.");
        // Kẻ tấn công thay payload bằng payload của user khác nhưng giữ nguyên chữ ký cũ
        String otherPayload = serviceAt(NOW, SECRET).generateAccessToken(userWithId(1L)).split("\\.")[1];
        String tampered = parts[0] + "." + otherPayload + "." + parts[2];

        assertThatThrownBy(() -> serviceAt(NOW, SECRET).parseUserId(tampered))
                .isInstanceOf(JwtException.class);
    }
}
