package com.sportbooking.integration.vnpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Ký và kiểm tra chữ ký theo đặc tả VNPay 2.1.0. Java thuần, không phụ thuộc Spring -> unit test dễ.
 *
 * Chữ ký = HMAC-SHA512(hashSecret, hashData), trong đó hashData:
 *   - bỏ các tham số rỗng và chính vnp_SecureHash / vnp_SecureHashType
 *   - sắp xếp theo TÊN tham số tăng dần
 *   - nối "ten=giá_trị_đã_URL-encode" bằng dấu &  (encode kiểu US-ASCII, dấu cách thành '+')
 *
 * Vì sao cần chữ ký? Ai cũng có thể gọi IPN URL hoặc sửa tham số trên trình duyệt (vd đổi
 * vnp_Amount, vnp_ResponseCode=00). Chỉ VNPay và mình biết hashSecret, nên chỉ VNPay tạo được
 * chữ ký khớp. Sửa bất kỳ tham số nào, chữ ký sẽ sai.
 */
public final class VnPaySigner {

    public static final String SECURE_HASH = "vnp_SecureHash";
    public static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    private VnPaySigner() {
    }

    /** Chuỗi dữ liệu dùng để ký: sắp xếp theo tên, giá trị đã URL-encode. */
    public static String hashData(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .filter(e -> !SECURE_HASH.equals(e.getKey()) && !SECURE_HASH_TYPE.equals(e.getKey()))
                .map(e -> e.getKey() + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /** Query string gửi lên VNPay (cả tên và giá trị đều encode). */
    public static String buildQuery(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String sign(Map<String, String> params, String secret) {
        return hmacSha512(secret, hashData(params));
    }

    /**
     * So sánh chữ ký VNPay gửi về với chữ ký tự tính.
     * MessageDigest.isEqual so sánh với thời gian không đổi: không để lộ "đúng được bao nhiêu ký tự đầu"
     * qua thời gian phản hồi (timing attack), điều mà String.equals có thể để lộ.
     */
    public static boolean verify(Map<String, String> params, String secret) {
        String received = params.get(SECURE_HASH);
        if (received == null || received.isBlank()) {
            return false;
        }
        String expected = sign(params, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    public static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot compute HMAC-SHA512", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
