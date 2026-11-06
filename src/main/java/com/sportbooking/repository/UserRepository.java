package com.sportbooking.repository;

import com.sportbooking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * JpaSpecificationExecutor: cho phép ghép các điều kiện lọc tùy chọn (role, status, keyword)
 * thành câu query động. Sẽ dùng nhiều hơn ở GĐ 3 (tìm sân).
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
