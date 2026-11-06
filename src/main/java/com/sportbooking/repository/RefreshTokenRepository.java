package com.sportbooking.repository;

import com.sportbooking.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * SELECT ... FOR UPDATE: khóa dòng token cho tới khi transaction kết thúc.
     * Nếu 2 request cùng dùng 1 refresh token, request thứ 2 phải đợi request thứ nhất xong
     * rồi mới đọc được -> lúc đó token đã bị thu hồi. (Chi tiết về lock ở GĐ 4.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Thu hồi toàn bộ phiên đăng nhập của 1 user bằng 1 câu UPDATE (không load từng entity). */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
