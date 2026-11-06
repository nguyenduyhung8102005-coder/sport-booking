package com.sportbooking.security;

import com.sportbooking.entity.User;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Chạy 1 lần cho mỗi request, TRƯỚC khi vào controller.
 *
 * Luồng: đọc header "Authorization: Bearer <token>" -> kiểm tra chữ ký & hạn -> lấy userId
 * -> load User từ DB -> nếu hợp lệ thì đặt vào SecurityContext.
 *
 * Filter KHÔNG tự trả lỗi 401. Nếu token sai, nó chỉ ghi lý do vào request attribute rồi
 * cho request đi tiếp. Nếu endpoint yêu cầu đăng nhập, Spring Security sẽ gọi
 * RestAuthenticationEntryPoint, nơi đọc lý do đó và trả JSON 401 phù hợp.
 * Nhờ vậy endpoint công khai (login, register) vẫn chạy bình thường dù client gửi token hỏng.
 *
 * KHÔNG đánh dấu @Component: nếu là bean, Spring Boot sẽ tự đăng ký thêm filter này vào
 * servlet container, khiến nó chạy ở ngoài Spring Security chain. Ta tạo nó bằng tay trong SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "sportbooking.auth.error";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Long userId = jwtService.parseUserId(token);
            Optional<User> user = userRepository.findById(userId);

            if (user.isEmpty()) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
            } else if (user.get().isLocked()) {
                // Khóa tài khoản có hiệu lực NGAY, không phải chờ access token hết hạn
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.ACCOUNT_LOCKED);
            } else {
                UserPrincipal principal = UserPrincipal.from(user.get());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            // Mã riêng để client biết: gọi /auth/refresh rồi thử lại
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException: subject không phải số, token rỗng...
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
        }

        filterChain.doFilter(request, response);
    }
}
