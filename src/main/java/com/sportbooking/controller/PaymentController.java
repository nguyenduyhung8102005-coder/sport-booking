package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.dto.response.PaymentResultResponse;
import com.sportbooking.dto.response.PaymentUrlResponse;
import com.sportbooking.integration.vnpay.VnPayIpnResponse;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.PaymentService;
import com.sportbooking.service.VnPayIpnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Payment", description = "Thanh toán VNPay")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final VnPayIpnService vnPayIpnService;

    @Operation(summary = "Tạo link thanh toán VNPay cho lượt đặt đang chờ thanh toán")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/bookings/{bookingId}/payments")
    public ResponseEntity<ApiResponse<PaymentUrlResponse>> createPayment(@AuthenticationPrincipal UserPrincipal customer,
                                                                         @PathVariable Long bookingId,
                                                                         HttpServletRequest request) {
        // Chạy sau reverse proxy (nginx) thì cần bật server.forward-headers-strategy để lấy đúng IP thật của khách
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(paymentService.createVnPayPayment(customer.id(), bookingId, request.getRemoteAddr())));
    }

    @Operation(summary = "[Dành cho VNPay] IPN: VNPay báo kết quả giao dịch",
            description = "Public, xác thực bằng chữ ký HMAC-SHA512. Trả đúng định dạng VNPay {RspCode, Message}.")
    @GetMapping("/payments/vnpay/ipn")
    public VnPayIpnResponse ipn(@RequestParam Map<String, String> params) {
        return vnPayIpnService.handleIpn(params);
    }

    @Operation(summary = "[Dành cho trình duyệt] VNPay chuyển khách về sau khi thanh toán",
            description = "Chỉ hiển thị kết quả, KHÔNG đổi trạng thái. Trong hệ thống thật sẽ chuyển tiếp sang trang frontend.")
    @GetMapping("/payments/vnpay/return")
    public ApiResponse<PaymentResultResponse> returnUrl(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(paymentService.getReturnResult(params));
    }
}
