-- =====================================================================
-- V4: Chuẩn bị cho đánh giá và job hoàn thành lượt đặt
-- =====================================================================

-- 1. Tổng điểm đánh giá: cho phép cập nhật điểm trung bình bằng 1 câu UPDATE NGUYÊN TỬ
--    (review_count + 1, rating_total + điểm mới), không cần đọc - tính - ghi.
ALTER TABLE venues ADD COLUMN rating_total BIGINT NOT NULL DEFAULT 0;

-- Bù dữ liệu cho bảng đã có review (nếu có)
UPDATE venues v
SET rating_total = COALESCE((SELECT SUM(r.rating) FROM reviews r WHERE r.venue_id = v.id), 0);

-- 2. Job tìm lượt đã thanh toán và đã qua giờ chơi: partial index chỉ chứa lượt CONFIRMED
CREATE INDEX idx_bookings_confirmed_end ON bookings (booking_date, end_time) WHERE status = 'CONFIRMED';
