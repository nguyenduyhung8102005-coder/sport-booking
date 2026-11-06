package com.sportbooking.api;

import com.sportbooking.config.BookingProperties;
import com.sportbooking.integration.vnpay.VnPayProperties;
import com.sportbooking.integration.vnpay.VnPaySigner;
import com.sportbooking.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ⭐ Hành trình đầy đủ qua HTTP, đúng như 1 client thật sẽ gọi:
 *
 *   1. Chủ sân đăng ký -> xin làm chủ sân -> ADMIN duyệt
 *   2. Chủ sân tạo cụm sân, sân con, bảng giá
 *   3. Khách tìm sân, xem giờ trống, đặt sân (người thứ 2 đặt trùng -> 409)
 *   4. Khách thanh toán, VNPay gọi IPN -> lượt đặt CONFIRMED, slot thành BOOKED
 *   5. Chơi xong -> khách đánh giá -> điểm của cụm sân cập nhật
 *   6. Chủ sân xem doanh thu
 *
 * Test service đã kiểm tra từng nghiệp vụ kỹ hơn. Test này bắt những lỗi mà test service không thấy:
 * sai URL, sai phân quyền, sai mã HTTP, tên field JSON đổi làm client hỏng, thiếu @Valid...
 */
class FullJourneyApiTest extends ApiTestBase {

    private static final long PRICE_PER_HOUR = 400_000;

    @Autowired
    private VnPayProperties vnPayProperties;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ownerOnboarding_booking_payment_review_revenue() throws Exception {
        // ===== 1. Chủ sân: đăng ký, xin làm chủ sân, ADMIN duyệt =====
        Account owner = register("owner");
        mockMvc.perform(as(owner, get("/api/v1/owner/venues")))
                .andExpect(status().isForbidden());                       // chưa là OWNER

        mockMvc.perform(as(owner, withJson(post("/api/v1/owner-applications"),
                        Map.of("businessName", "Công ty Sân Bóng Test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        Account admin = loginAsAdmin();
        mockMvc.perform(as(admin, post("/api/v1/admin/owner-applications/{userId}/approve", owner.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Token CŨ dùng được ngay với quyền mới: server đọc role từ DB ở mỗi request, không tin role trong token
        mockMvc.perform(as(owner, get("/api/v1/owner/venues")))
                .andExpect(status().isOk());

        // ===== 2. Chủ sân tạo cụm sân, sân con, bảng giá =====
        String venueName = "Sân API " + UUID.randomUUID();
        MvcResult venue = mockMvc.perform(as(owner, withJson(post("/api/v1/owner/venues"), Map.of(
                        "name", venueName,
                        "address", "1 Trần Phú",
                        "district", "Hà Đông",
                        "city", "Hà Nội",
                        "openTime", "06:00",
                        "closeTime", "23:00"))))
                .andExpect(status().isCreated())
                .andReturn();
        long venueId = readLong(venue, "$.data.id");

        MvcResult court = mockMvc.perform(as(owner, withJson(post("/api/v1/owner/venues/{venueId}/courts", venueId),
                        Map.of("name", "Sân 1", "sportType", "FOOTBALL_5"))))
                .andExpect(status().isCreated())
                .andReturn();
        long courtId = readLong(court, "$.data.id");

        mockMvc.perform(as(owner, withJson(put("/api/v1/owner/courts/{courtId}/price-rules", courtId), Map.of("rules", List.of(
                        Map.of("dayType", "WEEKDAY", "startTime", "06:00", "endTime", "23:00", "pricePerHour", PRICE_PER_HOUR),
                        Map.of("dayType", "WEEKEND", "startTime", "06:00", "endTime", "23:00", "pricePerHour", PRICE_PER_HOUR))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // ===== 3. Khách tìm sân, xem giờ trống, đặt sân =====
        Account customer = register("customer");
        LocalDate playDate = LocalDate.now(bookingProperties.zone()).plusDays(1);

        mockMvc.perform(get("/api/v1/venues").param("keyword", venueName))      // public, không cần token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(venueId));

        assertThat(slotStatus(courtId, playDate, "17:00")).isEqualTo("AVAILABLE");

        Map<String, Object> bookingRequest = Map.of(
                "courtId", courtId, "date", playDate.toString(), "startTime", "17:00", "endTime", "18:00");
        MvcResult booking = mockMvc.perform(as(customer, withJson(post("/api/v1/bookings"), bookingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalPrice").value(PRICE_PER_HOUR))   // server tự tính giá
                .andReturn();
        long bookingId = readLong(booking, "$.data.id");

        Account otherCustomer = register("customer");
        mockMvc.perform(as(otherCustomer, withJson(post("/api/v1/bookings"), bookingRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLOT_ALREADY_BOOKED"));

        // Khách khác không xem được lượt đặt của mình (404, không lộ là có tồn tại)
        mockMvc.perform(as(otherCustomer, get("/api/v1/bookings/{id}", bookingId)))
                .andExpect(status().isNotFound());

        // ===== 4. Thanh toán VNPay =====
        MvcResult payment = mockMvc.perform(as(customer, post("/api/v1/bookings/{id}/payments", bookingId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentUrl").value(containsString("vnp_SecureHash=")))
                .andReturn();

        mockMvc.perform(signedIpn(read(payment, "$.data.txnRef"), readLong(payment, "$.data.amount")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));

        mockMvc.perform(as(customer, get("/api/v1/bookings/{id}", bookingId)))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertThat(slotStatus(courtId, playDate, "17:00")).isEqualTo("BOOKED");

        // ===== 5. Chơi xong -> đánh giá =====
        // Chưa chơi thì chưa được đánh giá
        mockMvc.perform(as(customer, withJson(post("/api/v1/bookings/{id}/review", bookingId), Map.of("rating", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_ALLOWED"));

        // "Tua thời gian": đánh dấu đã chơi xong (thực tế do BookingCompletionJob làm sau giờ chơi)
        jdbc.update("UPDATE bookings SET status = 'COMPLETED' WHERE id = ?", bookingId);

        mockMvc.perform(as(customer, withJson(post("/api/v1/bookings/{id}/review", bookingId),
                        Map.of("rating", 5, "comment", "Sân đẹp, chủ sân nhiệt tình"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/venues/{id}", venueId))
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.avgRating").value(5.0));
        mockMvc.perform(get("/api/v1/venues/{id}/reviews", venueId))
                .andExpect(jsonPath("$.data.content[0].comment").value("Sân đẹp, chủ sân nhiệt tình"));

        // ===== 6. Chủ sân xem doanh thu =====
        mockMvc.perform(as(owner, get("/api/v1/owner/statistics/revenue")
                        .param("venueId", String.valueOf(venueId))
                        .param("from", playDate.toString())
                        .param("to", playDate.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBookings").value(1))
                .andExpect(jsonPath("$.data.totalRevenue").value(PRICE_PER_HOUR));
    }

    /** Trạng thái của slot bắt đầu lúc {@code startTime} trong lịch trống (API public). */
    private String slotStatus(long courtId, LocalDate date, String startTime) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/courts/{id}/availability", courtId).param("date", date.toString()))
                .andExpect(status().isOk())
                .andReturn();
        List<String> starts = read(result, "$.data.slots[*].startTime");
        List<String> statuses = read(result, "$.data.slots[*].status");
        for (int i = 0; i < starts.size(); i++) {
            if (starts.get(i).startsWith(startTime)) {
                return statuses.get(i);
            }
        }
        throw new AssertionError("Không có slot " + startTime);
    }

    /** Request IPN giống hệt VNPay gửi: tham số giao dịch + chữ ký HMAC bằng secret của merchant. */
    private MockHttpServletRequestBuilder signedIpn(String txnRef, long amount) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", vnPayProperties.tmnCode());
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", String.valueOf(amount * 100));          // VNPay tính theo đơn vị x100
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14000099");
        params.put(VnPaySigner.SECURE_HASH, VnPaySigner.sign(params, vnPayProperties.hashSecret()));

        MockHttpServletRequestBuilder request = get("/api/v1/payments/vnpay/ipn");
        params.forEach(request::param);
        return request;
    }
}
