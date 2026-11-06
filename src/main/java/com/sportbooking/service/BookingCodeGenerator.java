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
        StringBuilder code = new StringBuilder("BK").append(DATE_PART.format(today));
        for (int i = 0; i < 6; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
