package com.sportbooking.dto.response;

import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.entity.enums.PaymentStatus;

/**
 * Kết quả hiển thị cho khách khi VNPay chuyển trình duyệt về return URL.
 * CHỈ ĐỂ HIỂN THỊ: trạng thái thật do IPN cập nhật, có thể chưa tới kịp lúc trình duyệt quay về.
 */
public record PaymentResultResponse(
        String bookingCode,
        String txnRef,
        String vnpResponseCode,
        PaymentStatus paymentStatus,
        BookingStatus bookingStatus,
        String message) {
}
