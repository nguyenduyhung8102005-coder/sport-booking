-- =====================================================================
-- V3: Thêm trạng thái thanh toán REFUND_REQUIRED
--
-- Tình huống: VNPay báo khách đã trả tiền thành công, nhưng lượt đặt KHÔNG còn giữ chỗ
-- (đã hết hạn và slot có thể đã thuộc về người khác, đã bị hủy, hoặc đã được trả bằng
-- một giao dịch khác). Hệ thống ghi nhận đã nhận tiền nhưng không giao được sân -> cần hoàn tiền.
-- =====================================================================

ALTER TABLE payments DROP CONSTRAINT ck_payments_status;
ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUND_REQUIRED'));
