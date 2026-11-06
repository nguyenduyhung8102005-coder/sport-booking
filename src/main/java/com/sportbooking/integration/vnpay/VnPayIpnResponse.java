package com.sportbooking.integration.vnpay;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phản hồi cho VNPay theo ĐÚNG định dạng VNPay quy định: {"RspCode":"00","Message":"Confirm Success"}.
 * KHÔNG bọc trong ApiResponse như các API khác, vì người đọc là máy chủ VNPay chứ không phải client của ta.
 *
 * VNPay dừng gửi lại khi nhận 00 hoặc 02; với 01, 04, 97, 99 (hoặc timeout) VNPay gửi lại IPN
 * (tối đa 10 lần, mỗi 5 phút).
 */
public record VnPayIpnResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message) {

    public static final VnPayIpnResponse CONFIRM_SUCCESS = new VnPayIpnResponse("00", "Confirm Success");
    public static final VnPayIpnResponse ORDER_NOT_FOUND = new VnPayIpnResponse("01", "Order not found");
    public static final VnPayIpnResponse ALREADY_CONFIRMED = new VnPayIpnResponse("02", "Order already confirmed");
    public static final VnPayIpnResponse INVALID_AMOUNT = new VnPayIpnResponse("04", "invalid amount");
    public static final VnPayIpnResponse INVALID_SIGNATURE = new VnPayIpnResponse("97", "Invalid signature");
    public static final VnPayIpnResponse UNKNOWN_ERROR = new VnPayIpnResponse("99", "Unknown error");
}
