package com.sportbooking.repository;

import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.Role;
import com.sportbooking.entity.enums.UserStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Mỗi method trả về 1 điều kiện WHERE. Tham số null/rỗng -> điều kiện luôn đúng (bỏ qua bộ lọc).
 * Ghép lại bằng Specification.allOf(...).
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> role == null ? cb.conjunction() : cb.equal(root.get("role"), role);
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    /** Tìm theo email hoặc họ tên, không phân biệt hoa thường. */
    public static Specification<User> matchesKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern));
        };
    }
}
