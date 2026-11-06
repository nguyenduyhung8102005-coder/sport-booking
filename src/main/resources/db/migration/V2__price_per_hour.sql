-- =====================================================================
-- V2: Lưu giá theo GIỜ thay vì theo slot 30 phút
--
-- Lý do: chủ sân và khách đều nghĩ theo "300k/giờ", không ai nói "150k/30 phút".
-- Giá bắt buộc là bội số của 1.000đ -> giá 1 slot (= giá giờ / 2) luôn là số nguyên.
--
-- Đây là ví dụ cho quy tắc Flyway: KHÔNG sửa V1 đã chạy, mà thêm V2.
-- =====================================================================

ALTER TABLE price_rules RENAME COLUMN price_per_slot TO price_per_hour;

ALTER TABLE price_rules DROP CONSTRAINT ck_price_rules_price;
ALTER TABLE price_rules ADD CONSTRAINT ck_price_rules_price
    CHECK (price_per_hour > 0 AND price_per_hour % 1000 = 0);
