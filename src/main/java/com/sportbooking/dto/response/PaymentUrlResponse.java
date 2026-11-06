package com.sportbooking.dto.response;

import java.time.Instant;

/** Client mở paymentUrl (chuyển trình duyệt sang trang VNPay) để khách thanh toán. */
public record PaymentUrlResponse(
        String txnRef,
        long amount,
        String paymentUrl,
        Instant expiresAt) {
}
