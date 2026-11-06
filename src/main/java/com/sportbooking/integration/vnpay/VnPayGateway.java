package com.sportbooking.integration.vnpay;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Lớp "adapter" duy nhất biết về định dạng của VNPay. Phần còn lại của hệ thống chỉ gọi
 * buildPaymentUrl / verifySignature, nên đổi sang cổng thanh toán khác (MoMo, ZaloPay)
 * chỉ cần viết adapter mới.
 */
@Component
@RequiredArgsConstructor
public class VnPayGateway {

    /** VNPay yêu cầu thời gian dạng yyyyMMddHHmmss theo giờ GMT+7. */
    public static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayProperties properties;

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * @param amountVnd  số tiền VND (VNPay nhận số tiền x 100)
     * @param createDate thời điểm tạo, giờ Việt Nam
     * @param expireDate quá thời điểm này VNPay không cho thanh toán nữa, giờ Việt Nam
     */
    public String buildPaymentUrl(String txnRef, long amountVnd, String orderInfo, String clientIp,
                                  LocalDateTime createDate, LocalDateTime expireDate) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", properties.version());
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.tmnCode());
        params.put("vnp_Amount", String.valueOf(amountVnd * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);     // tiếng Việt KHÔNG dấu theo yêu cầu của VNPay
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", properties.returnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", VNPAY_TIME.format(createDate));
        params.put("vnp_ExpireDate", VNPAY_TIME.format(expireDate));

        String signature = VnPaySigner.sign(params, properties.hashSecret());
        return properties.payUrl() + "?" + VnPaySigner.buildQuery(params)
                + "&" + VnPaySigner.SECURE_HASH + "=" + signature;
    }

    public boolean verifySignature(Map<String, String> params) {
        return VnPaySigner.verify(params, properties.hashSecret());
    }
}
