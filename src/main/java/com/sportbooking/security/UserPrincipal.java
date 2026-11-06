package com.sportbooking.security;

import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Thông tin "ai đang gọi API" được đặt vào SecurityContext.
 * Controller lấy ra bằng @AuthenticationPrincipal UserPrincipal principal.
 * Chỉ giữ vài field nhẹ, không giữ cả entity User (tránh lazy loading ngoài transaction).
 */
public record UserPrincipal(Long id, String email, Role role) {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole());
    }

    /** Spring Security quy ước quyền theo role có tiền tố "ROLE_" -> hasRole("ADMIN") khớp "ROLE_ADMIN". */
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
