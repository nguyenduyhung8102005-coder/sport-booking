package com.sportbooking.controller;

import com.sportbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gọi qua HTTP thật (MockMvc) để kiểm tra những thứ test service không thấy được:
 * endpoint public (không cần JWT) và JSON trả về đúng định dạng VNPay quy định.
 */
@AutoConfigureMockMvc
class VnPayEndpointsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ipn_isPublic_andAnswersInVnPayFormat_notWrappedInApiResponse() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "BK000000ABCDEF")
                        .param("vnp_Amount", "50000000")
                        .param("vnp_SecureHash", "chu-ky-gia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").value("Invalid signature"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void returnUrl_withForgedSignature_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/return")
                        .param("vnp_TxnRef", "BK000000ABCDEF")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "chu-ky-gia"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_SIGNATURE"));
    }

    @Test
    void createPayment_requiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/1/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
