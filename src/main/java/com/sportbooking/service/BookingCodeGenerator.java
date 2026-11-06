package com.sportbooking.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Mã đặt sân để khách đọc cho chủ sân: "BK261107K7XM2Q" (BK + ngày tạo + 6 ký tự ngẫu nhiên).
 * Bỏ các ký tự dễ nhầm khi đọc: 0/O, 1/I.
 * 32^6 ≈ 1 tỷ tổ hợp mỗi ngày; nếu trùng (cực hiếm), UNIQUE uk_bookings_code ở DB vẫn chặn.
 */
public final class BookingCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingCodeGenerator() {
    }

    public static String generate(LocalDate today) {
        return "BK" + DATE_PART.format(today) + randomChars(6);
    }

    /** Chuỗi ngẫu nhiên chỉ gồm chữ in hoa và số (VNPay yêu cầu vnp_TxnRef chỉ có chữ và số). */
    public static String randomChars(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return value.toString();
    }
}
