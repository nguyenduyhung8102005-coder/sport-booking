package com.sportbooking.entity.enums;

/**
 * PENDING         : đã tạo link thanh toán, chờ VNPay báo kết quả (IPN)
 * SUCCESS         : VNPay báo trả tiền thành công VÀ lượt đặt đã được xác nhận
 * FAILED          : khách hủy giao dịch, thẻ lỗi, hết hạn... (lượt đặt vẫn PENDING, khách có thể thử lại)
 * REFUND_REQUIRED : đã nhận tiền nhưng không giao được sân (lượt đặt đã hết hạn/bị hủy/đã trả bằng giao dịch khác)
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUND_REQUIRED
}
