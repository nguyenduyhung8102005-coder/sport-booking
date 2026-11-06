package com.sportbooking.service;

import com.sportbooking.config.JwtProperties;
import com.sportbooking.dto.request.LoginRequest;
import com.sportbooking.dto.request.RegisterRequest;
import com.sportbooking.dto.response.AuthResponse;
import com.sportbooking.entity.RefreshToken;
import com.sportbooking.entity.User;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.mapper.UserMapper;
import com.sportbooking.repository.RefreshTokenRepository;
import com.sportbooking.repository.UserRepository;
import com.sportbooking.security.JwtService;
import com.sportbooking.security.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        // Kiểm tra trước để trả lỗi thân thiện trong trường hợp thường gặp
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.newCustomer(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.phone());
        try {
            // saveAndFlush: chạy INSERT ngay để bắt lỗi trùng email tại đây.
            // Trường hợp 2 người đăng ký cùng email ĐỒNG THỜI: cả 2 đều qua được existsByEmail,
            // nhưng UNIQUE constraint ở DB chỉ cho 1 người thành công.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Email không tồn tại và sai mật khẩu trả CÙNG một lỗi:
        // không để kẻ xấu dò xem email nào đã đăng ký trong hệ thống.
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (user.isLocked()) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }
        return issueTokens(user);
    }

    /**
     * Refresh token rotation: mỗi refresh token chỉ dùng được ĐÚNG 1 LẦN.
     * Dùng xong -> thu hồi và cấp token mới.
     *
     * Phát hiện dùng lại (reuse detection): nếu một token ĐÃ bị thu hồi mà vẫn có người gửi lên,
     * nghĩa là có 2 bên đang giữ cùng token -> khả năng bị đánh cắp -> thu hồi TOÀN BỘ phiên của user.
     *
     * noRollbackFor: lệnh thu hồi toàn bộ phải được COMMIT dù sau đó ta ném AppException.
     * Mặc định @Transactional rollback khi gặp RuntimeException, sẽ xóa mất lệnh thu hồi.
     */
    @Transactional(noRollbackFor = AppException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256(rawRefreshToken))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (token.isRevoked()) {
            Long userId = token.getUser().getId();
            int revoked = refreshTokenRepository.revokeAllByUserId(userId, now);
            log.warn("Refresh token reuse detected for userId={}, revoked {} active session(s)", userId, revoked);
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (token.isExpired(now)) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = token.getUser();
        if (user.isLocked()) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        token.revoke(now);   // dirty checking: Hibernate tự UPDATE khi transaction commit
        return issueTokens(user);
    }

    /**
     * Thu hồi refresh token của phiên hiện tại. Idempotent: gọi nhiều lần vẫn trả thành công.
     * Lưu ý: access token vẫn dùng được tới khi hết hạn (tối đa 15 phút).
     * Muốn chặn ngay cần blacklist (sẽ làm bằng Redis sau MVP).
     */
    @Transactional
    public void logout(Long currentUserId, String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256(rawRefreshToken))
                .filter(token -> token.getUser().getId().equals(currentUserId))   // không cho thu hồi token của người khác
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = TokenUtils.randomToken();

        Instant refreshExpiresAt = clock.instant().plus(jwtProperties.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user, TokenUtils.sha256(rawRefreshToken), refreshExpiresAt));

        // Token gốc chỉ xuất hiện đúng 1 lần, trong response này. DB chỉ giữ hash.
        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                TOKEN_TYPE,
                jwtProperties.accessTokenTtl().toSeconds(),
                UserMapper.toResponse(user));
    }

    /** "  An@Gmail.com " và "an@gmail.com" phải được coi là cùng 1 tài khoản. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
