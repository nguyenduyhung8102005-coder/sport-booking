package com.sportbooking.api;

import com.sportbooking.entity.User;
import com.sportbooking.repository.UserRepository;
import com.sportbooking.config.JwtProperties;
import com.sportbooking.security.JwtService;
import com.sportbooking.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ma trận phân quyền và các tình huống token: kiểm tra qua HTTP thật vì phân quyền nằm ở
 * Security filter chain, test service không đi qua đó.
 */
class SecurityApiTest extends ApiTestBase {

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private UserRepository userRepository;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/me", "/api/v1/bookings/me", "/api/v1/owner/venues", "/api/v1/admin/users"})
    void withoutToken_protectedEndpoints_return401(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void roles_areEnforced_byUrl() throws Exception {
        Account customer = register("customer");
        mockMvc.perform(as(customer, get("/api/v1/owner/venues"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(as(customer, get("/api/v1/admin/users"))).andExpect(status().isForbidden());
        mockMvc.perform(as(customer, get("/api/v1/bookings/me"))).andExpect(status().isOk());

        Account admin = loginAsAdmin();
        mockMvc.perform(as(admin, get("/api/v1/admin/users"))).andExpect(status().isOk());
        // ADMIN không tự động có quyền OWNER: mỗi role chỉ vào đúng khu vực của mình
        mockMvc.perform(as(admin, get("/api/v1/owner/venues"))).andExpect(status().isForbidden());
    }

    @Test
    void publicEndpoints_workWithoutToken_evenWithBrokenToken() throws Exception {
        mockMvc.perform(get("/api/v1/venues")).andExpect(status().isOk());
        // Token hỏng KHÔNG làm hỏng endpoint public: filter chỉ ghi lý do, không tự trả 401
        mockMvc.perform(get("/api/v1/venues").header(HttpHeaders.AUTHORIZATION, "Bearer rac"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                // OpenApiCustomizer tự thêm response lỗi chung cho endpoint cần đăng nhập
                .andExpect(jsonPath("$.paths['/api/v1/bookings'].post.responses['401']").exists());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void tamperedToken_isRejected() throws Exception {
        Account customer = register("customer");
        String[] parts = customer.accessToken().split("\\.");   // header.payload.signature
        // Kẻ tấn công sửa payload thành userId=1 (thường là admin) nhưng giữ chữ ký cũ.
        // JWT chỉ được mã hóa Base64 (ai cũng đọc/sửa được), KHÔNG được mã hóa bí mật;
        // thứ bảo vệ nó là chữ ký: sửa 1 byte payload là chữ ký không còn khớp.
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredToken_hasDedicatedErrorCode_soClientKnowsToRefresh() throws Exception {
        Account customer = register("customer");
        User user = userRepository.findById(customer.id()).orElseThrow();
        // Token được ký ĐÚNG khóa nhưng phát hành 1 giờ trước (TTL 15 phút) -> đã hết hạn
        Clock oneHourAgo = Clock.fixed(Instant.now().minus(1, ChronoUnit.HOURS), ZoneOffset.UTC);
        String expired = new JwtService(jwtProperties, oneHourAgo).generateAccessToken(user);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void lockingUser_takesEffectImmediately_onExistingToken() throws Exception {
        Account customer = register("customer");
        mockMvc.perform(as(customer, get("/api/v1/users/me"))).andExpect(status().isOk());

        Account admin = loginAsAdmin();
        mockMvc.perform(as(admin, withJson(patch("/api/v1/admin/users/{id}/status", customer.id()),
                        Map.of("status", "LOCKED"))))
                .andExpect(status().isOk());

        // Access token vẫn còn hạn nhưng bị chặn ngay, vì mỗi request đều đọc trạng thái user từ DB
        mockMvc.perform(as(customer, get("/api/v1/users/me")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshToken_rotates_andOldOneCannotBeReused() throws Exception {
        Account customer = register("customer");

        mockMvc.perform(withJson(post("/api/v1/auth/refresh"), Map.of("refreshToken", customer.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").value(not(customer.refreshToken())));

        // Dùng lại refresh token cũ (vd: bị đánh cắp) -> từ chối
        mockMvc.perform(withJson(post("/api/v1/auth/refresh"), Map.of("refreshToken", customer.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void wrongPassword_returnsGenericError() throws Exception {
        Account customer = register("customer");
        // Cùng 1 thông báo cho "sai mật khẩu" và "email không tồn tại": không cho kẻ tấn công dò email
        mockMvc.perform(withJson(post("/api/v1/auth/login"), Map.of("email", customer.email(), "password", "SaiMatKhau1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(withJson(post("/api/v1/auth/login"), Map.of("email", uniqueEmail("ghost"), "password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }
}
