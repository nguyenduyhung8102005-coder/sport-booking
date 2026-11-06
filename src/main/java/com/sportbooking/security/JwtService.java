package com.sportbooking.security;

import com.sportbooking.config.JwtProperties;
import com.sportbooking.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * Tạo và kiểm tra access token (JWT ký bằng HMAC-SHA256).
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // Khóa < 256 bit -> jjwt ném WeakKeyException ngay lúc khởi động (fail fast)
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    /**
     * subject = userId (không dùng email vì email có thể đổi).
     * role chỉ để client biết hiển thị giao diện gì; server KHÔNG tin role trong token,
     * mà luôn đọc role mới nhất từ database (xem JwtAuthenticationFilter).
     */
    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * Kiểm tra chữ ký + hạn dùng, trả về userId.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException token đã hết hạn
     * @throws io.jsonwebtoken.JwtException        token bị sửa, sai chữ ký, sai định dạng
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
