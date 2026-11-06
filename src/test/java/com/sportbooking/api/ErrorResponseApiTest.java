package com.sportbooking.api;

import com.sportbooking.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Hợp đồng" lỗi với client: dù lỗi ở đâu (validation, JSON hỏng, sai kiểu, sai URL...),
 * client luôn nhận cùng 1 dạng JSON: { success: false, error: { code, message, details? }, timestamp }
 * và KHÔNG bao giờ thấy stack trace hay thông báo kỹ thuật của Java.
 */
class ErrorResponseApiTest extends ApiTestBase {

    /** Kiểm tra phần chung mà MỌI response lỗi phải có. */
    private static ResultActions expectErrorShape(ResultActions result, String code) throws Exception {
        return result
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))));   // không lộ chi tiết kỹ thuật
    }

    @Test
    void validationErrors_listEveryInvalidField() throws Exception {
        expectErrorShape(mockMvc.perform(withJson(post("/api/v1/auth/register"), Map.of(
                        "email", "khong-phai-email",
                        "password", "123",
                        "fullName", ""))), "VALIDATION_FAILED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[*].field", containsInAnyOrder("email", "password", "password", "fullName")))
                .andExpect(jsonPath("$.error.details[*].message", hasItem("Email không hợp lệ")));
    }

    @Test
    void malformedJson_isBadRequest() throws Exception {
        expectErrorShape(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"a@b.vn\", ")), "BAD_REQUEST")
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongTypeInPathOrQuery_isBadRequest() throws Exception {
        Account customer = register("customer");
        expectErrorShape(mockMvc.perform(as(customer, get("/api/v1/bookings/abc"))), "BAD_REQUEST")
                .andExpect(status().isBadRequest());
        expectErrorShape(mockMvc.perform(get("/api/v1/courts/1/availability").param("date", "2026-13-45")), "BAD_REQUEST")
                .andExpect(status().isBadRequest());
        expectErrorShape(mockMvc.perform(get("/api/v1/courts/1/availability")), "BAD_REQUEST")   // thiếu ?date
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSearchParams_areRejected_withFieldDetails() throws Exception {
        expectErrorShape(mockMvc.perform(get("/api/v1/venues").param("minPrice", "-1").param("maxPrice", "-5")),
                "VALIDATION_FAILED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[*].field", containsInAnyOrder("minPrice", "maxPrice")));
    }

    @Test
    void sortingByNonWhitelistedField_isRejected() throws Exception {
        // Nếu cho sort tự do, client có thể sort theo cột không có index (chậm) hoặc dò dữ liệu nhạy cảm
        expectErrorShape(mockMvc.perform(get("/api/v1/venues").param("sort", "owner.passwordHash")), "BAD_REQUEST")
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageSize_isCappedAt100() throws Exception {
        mockMvc.perform(get("/api/v1/venues").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    void unknownEndpoint_andWrongMethod() throws Exception {
        Account customer = register("customer");
        expectErrorShape(mockMvc.perform(as(customer, get("/api/v1/khong-ton-tai"))), "RESOURCE_NOT_FOUND")
                .andExpect(status().isNotFound());
        expectErrorShape(mockMvc.perform(as(customer, delete("/api/v1/venues"))), "METHOD_NOT_ALLOWED")
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void businessErrors_useSpecificCodes() throws Exception {
        Account customer = register("customer");
        // Đăng ký trùng email -> 409 với mã nghiệp vụ riêng để client hiển thị đúng thông báo
        expectErrorShape(mockMvc.perform(withJson(post("/api/v1/auth/register"), Map.of(
                        "email", customer.email(), "password", PASSWORD, "fullName", "Trùng email"))), "EMAIL_ALREADY_EXISTS")
                .andExpect(status().isConflict());
        expectErrorShape(mockMvc.perform(get("/api/v1/venues/{id}", Long.MAX_VALUE)), "RESOURCE_NOT_FOUND")
                .andExpect(status().isNotFound());
    }
}
