package com.sportbooking.integration.vnpay;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnPaySignerTest {

    private static final String SECRET = "TEST_SECRET";

    private static Map<String, String> sampleParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "BK261107K7XM2QABC123");
        params.put("vnp_Amount", "50000000");
        params.put("vnp_OrderInfo", "Thanh toan dat san BK261107K7XM2Q");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", "TESTTMN1");
        return params;
    }

    @Test
    void hmacSha512_matchesKnownTestVector() {
        // Bộ dữ liệu chuẩn HMAC-SHA512 (đã đối chiếu bằng: openssl dgst -sha512 -hmac key)
        assertThat(VnPaySigner.hmacSha512("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb"
                        + "82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a");
    }

    @Test
    void hashData_sortsByName_encodesValues_skipsEmptyAndSignatureFields() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_B", "a b");
        params.put("vnp_A", "http://x/y");
        params.put("vnp_Empty", "");
        params.put(VnPaySigner.SECURE_HASH, "abc");
        params.put(VnPaySigner.SECURE_HASH_TYPE, "HmacSHA512");

        assertThat(VnPaySigner.hashData(params)).isEqualTo("vnp_A=http%3A%2F%2Fx%2Fy&vnp_B=a+b");
    }

    @Test
    void signedParams_verifySuccessfully_evenIfSignatureIsUppercase() {
        Map<String, String> params = sampleParams();
        params.put(VnPaySigner.SECURE_HASH, VnPaySigner.sign(params, SECRET).toUpperCase(Locale.ROOT));

        assertThat(VnPaySigner.verify(params, SECRET)).isTrue();
    }

    @Test
    void tamperedAmount_failsVerification() {
        Map<String, String> params = sampleParams();
        params.put(VnPaySigner.SECURE_HASH, VnPaySigner.sign(params, SECRET));

        params.put("vnp_Amount", "100");   // kẻ gian sửa số tiền trên URL

        assertThat(VnPaySigner.verify(params, SECRET)).isFalse();
    }

    @Test
    void wrongSecret_orMissingSignature_failsVerification() {
        Map<String, String> params = sampleParams();
        params.put(VnPaySigner.SECURE_HASH, VnPaySigner.sign(params, "ANOTHER_SECRET"));
        assertThat(VnPaySigner.verify(params, SECRET)).isFalse();

        params.remove(VnPaySigner.SECURE_HASH);
        assertThat(VnPaySigner.verify(params, SECRET)).isFalse();
    }
}
