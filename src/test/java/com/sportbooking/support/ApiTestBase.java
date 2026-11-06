package com.sportbooking.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lớp cha cho API test: gọi ứng dụng qua HTTP (MockMvc) giống hệt 1 client thật,
 * đi qua ĐỦ các lớp: Security filter -> controller -> validation -> service -> DB -> JSON.
 *
 * MockMvc không mở cổng mạng thật (nhanh hơn), nhưng chạy đúng DispatcherServlet và filter chain.
 * Dùng chung cấu hình với VnPayEndpointsIntegrationTest nên Spring tái sử dụng cùng 1 context (không khởi động lại).
 *
 * Body request viết bằng Map với giá trị chuỗi (vd "17:00") để giống JSON client gửi thật,
 * thay vì dựa vào cách Jackson tự tuần tự hóa record của server.
 */
@AutoConfigureMockMvc
public abstract class ApiTestBase extends IntegrationTestBase {

    protected static final String PASSWORD = "Password123";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${app.admin.email}")
    private String adminEmail;
    @Value("${app.admin.password}")
    private String adminPassword;

    /** Thông tin 1 tài khoản sau khi đăng ký / đăng nhập qua API. */
    protected record Account(Long id, String email, String accessToken, String refreshToken) {
    }

    // ---------- request helpers ----------

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Gắn JSON body vào request. */
    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    /** Gắn header "Authorization: Bearer <token>". */
    protected MockHttpServletRequestBuilder as(Account account, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + account.accessToken());
    }

    // ---------- đọc response ----------

    /** Đọc body theo UTF-8 (mặc định Servlet đọc ISO-8859-1, chữ tiếng Việt sẽ bị lỗi font). */
    protected static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static <T> T read(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(body(result), jsonPath);
    }

    /** JsonPath trả Integer cho số nhỏ, Long cho số lớn -> chuẩn hóa về long. */
    protected static long readLong(MvcResult result, String jsonPath) throws Exception {
        return ((Number) read(result, jsonPath)).longValue();
    }

    // ---------- tài khoản ----------

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.vn";
    }

    protected Account register(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        MvcResult result = mockMvc.perform(withJson(post("/api/v1/auth/register"), Map.of(
                        "email", email,
                        "password", PASSWORD,
                        "fullName", "Người dùng " + prefix)))
                .andExpect(status().isCreated())
                .andReturn();
        return toAccount(result, email);
    }

    protected Account login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(withJson(post("/api/v1/auth/login"),
                        Map.of("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return toAccount(result, email);
    }

    /** Tài khoản ADMIN được AdminAccountInitializer tạo sẵn khi ứng dụng khởi động. */
    protected Account loginAsAdmin() throws Exception {
        return login(adminEmail, adminPassword);
    }

    private static Account toAccount(MvcResult result, String email) throws Exception {
        return new Account(readLong(result, "$.data.user.id"), email,
                read(result, "$.data.accessToken"), read(result, "$.data.refreshToken"));
    }
}
