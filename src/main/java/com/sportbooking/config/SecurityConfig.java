package com.sportbooking.config;

import com.sportbooking.repository.UserRepository;
import com.sportbooking.security.JwtAuthenticationFilter;
import com.sportbooking.security.JwtService;
import com.sportbooking.security.RestAccessDeniedHandler;
import com.sportbooking.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error"
    };

    /** Xem thông tin sân không cần đăng nhập (endpoint sẽ có ở GĐ 3). */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/venues/**",
            "/api/v1/courts/**"
    };

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF tấn công dựa vào cookie mà trình duyệt TỰ gửi kèm. API này xác thực bằng header
                // Authorization do client chủ động gắn -> không bị CSRF -> tắt.
                .csrf(AbstractHttpConfigurer::disable)
                // Không tạo HttpSession: mỗi request tự mang token, server không nhớ gì giữa các request
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // Quy tắc được xét TỪ TRÊN XUỐNG, gặp quy tắc khớp đầu tiên là dừng
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/owner/**").hasRole("OWNER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)   // chưa đăng nhập -> 401
                        .accessDeniedHandler(accessDeniedHandler))            // không đủ quyền -> 403
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt: chậm có chủ đích + salt ngẫu nhiên -> chống brute-force và rainbow table. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
