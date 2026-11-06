-- =====================================================================
-- V1: Schema ban đầu cho hệ thống đặt sân
-- QUY TẮC: file này đã chạy thì KHÔNG sửa. Mọi thay đổi -> tạo V2, V3...
-- =====================================================================

-- Hàm dùng lại trong CHECK: thời điểm phải tròn mốc :00 hoặc :30 (slot 30 phút)
CREATE FUNCTION is_half_hour(t TIME) RETURNS BOOLEAN
    LANGUAGE sql IMMUTABLE
AS $$ SELECT EXTRACT(MINUTE FROM t) IN (0, 30) AND EXTRACT(SECOND FROM t) = 0 $$;

-- ========== USERS & AUTH ==========
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email  UNIQUE (email),
    CONSTRAINT ck_users_role   CHECK (role IN ('CUSTOMER', 'OWNER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED'))
);

CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE owner_profiles (
    user_id         BIGINT       PRIMARY KEY REFERENCES users (id),
    business_name   VARCHAR(150) NOT NULL,
    approval_status VARCHAR(20)  NOT NULL,
    reviewed_by     BIGINT       REFERENCES users (id),
    reviewed_at     TIMESTAMPTZ,
    reject_reason   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_owner_profiles_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_owner_profiles_status ON owner_profiles (approval_status);

-- ========== VENUES, COURTS, PRICING ==========
CREATE TABLE venues (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id     BIGINT       NOT NULL REFERENCES users (id),
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    address      VARCHAR(255) NOT NULL,
    district     VARCHAR(100) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    open_time    TIME         NOT NULL,
    close_time   TIME         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    avg_rating   NUMERIC(2,1) NOT NULL DEFAULT 0,
    review_count INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_venues_hours   CHECK (close_time > open_time),
    CONSTRAINT ck_venues_aligned CHECK (is_half_hour(open_time) AND is_half_hour(close_time)),
    CONSTRAINT ck_venues_status  CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX idx_venues_owner    ON venues (owner_id);
CREATE INDEX idx_venues_location ON venues (city, district);

CREATE TABLE courts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id   BIGINT      NOT NULL REFERENCES venues (id),
    name       VARCHAR(50) NOT NULL,
    sport_type VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- index của UNIQUE này bắt đầu bằng venue_id nên cũng phục vụ truy vấn theo venue
    CONSTRAINT uk_courts_venue_name UNIQUE (venue_id, name),
    CONSTRAINT ck_courts_sport_type CHECK (sport_type IN ('FOOTBALL_5', 'FOOTBALL_7', 'BADMINTON')),
    CONSTRAINT ck_courts_status     CHECK (status IN ('ACTIVE', 'MAINTENANCE'))
);
CREATE INDEX idx_courts_sport_type ON courts (sport_type);

CREATE TABLE price_rules (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    court_id       BIGINT      NOT NULL REFERENCES courts (id) ON DELETE CASCADE,
    day_type       VARCHAR(10) NOT NULL,
    start_time     TIME        NOT NULL,
    end_time       TIME        NOT NULL,
    price_per_slot BIGINT      NOT NULL,
    label          VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_price_rules_day_type CHECK (day_type IN ('WEEKDAY', 'WEEKEND')),
    CONSTRAINT ck_price_rules_range    CHECK (end_time > start_time),
    CONSTRAINT ck_price_rules_aligned  CHECK (is_half_hour(start_time) AND is_half_hour(end_time)),
    CONSTRAINT ck_price_rules_price    CHECK (price_per_slot > 0),
    CONSTRAINT ck_price_rules_label    CHECK (label IN ('NORMAL', 'PEAK'))
);
CREATE INDEX idx_price_rules_court_day ON price_rules (court_id, day_type);

-- ========== BOOKINGS ==========
CREATE TABLE bookings (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_code  VARCHAR(20) NOT NULL,
    customer_id   BIGINT      NOT NULL REFERENCES users (id),
    court_id      BIGINT      NOT NULL REFERENCES courts (id),
    booking_date  DATE        NOT NULL,
    start_time    TIME        NOT NULL,
    end_time      TIME        NOT NULL,
    total_price   BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    expires_at    TIMESTAMPTZ,
    cancelled_at  TIMESTAMPTZ,
    cancel_reason TEXT,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_bookings_code           UNIQUE (booking_code),
    CONSTRAINT ck_bookings_range          CHECK (end_time > start_time),
    CONSTRAINT ck_bookings_aligned        CHECK (is_half_hour(start_time) AND is_half_hour(end_time)),
    CONSTRAINT ck_bookings_price          CHECK (total_price > 0),
    CONSTRAINT ck_bookings_status         CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'COMPLETED')),
    -- Booking PENDING bắt buộc có hạn giữ chỗ, nếu không scheduler sẽ không bao giờ hủy được
    CONSTRAINT ck_bookings_pending_expiry CHECK (status <> 'PENDING' OR expires_at IS NOT NULL)
);
CREATE INDEX idx_bookings_customer       ON bookings (customer_id, created_at DESC);
CREATE INDEX idx_bookings_court_date     ON bookings (court_id, booking_date);
-- Partial index: chỉ chứa booking đang chờ thanh toán -> nhỏ, scheduler quét nhanh
CREATE INDEX idx_bookings_pending_expiry ON bookings (expires_at) WHERE status = 'PENDING';

-- "Sổ giữ chỗ": chỉ tồn tại khi slot đang bị giữ. Hủy/hết hạn thì xóa dòng.
-- UNIQUE bên dưới là lớp chống double booking.
CREATE TABLE booking_slots (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    court_id   BIGINT NOT NULL REFERENCES courts (id),
    slot_date  DATE   NOT NULL,
    slot_start TIME   NOT NULL,
    CONSTRAINT uk_booking_slots_court_date_start UNIQUE (court_id, slot_date, slot_start),
    CONSTRAINT ck_booking_slots_aligned          CHECK (is_half_hour(slot_start))
);
CREATE INDEX idx_booking_slots_booking ON booking_slots (booking_id);

-- ========== PAYMENTS & REVIEWS ==========
CREATE TABLE payments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id      BIGINT      NOT NULL REFERENCES bookings (id),
    txn_ref         VARCHAR(50) NOT NULL,
    amount          BIGINT      NOT NULL,
    provider        VARCHAR(20) NOT NULL DEFAULT 'VNPAY',
    status          VARCHAR(20) NOT NULL,
    provider_txn_no VARCHAR(50),
    response_code   VARCHAR(10),
    bank_code       VARCHAR(20),
    raw_callback    JSONB,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_payments_txn_ref UNIQUE (txn_ref),
    CONSTRAINT ck_payments_amount  CHECK (amount > 0),
    CONSTRAINT ck_payments_status  CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);
CREATE INDEX idx_payments_booking ON payments (booking_id);
-- Một booking chỉ có tối đa 1 payment SUCCESS (lớp bảo vệ cuối chống ghi nhận trả tiền 2 lần)
CREATE UNIQUE INDEX uk_payments_booking_success ON payments (booking_id) WHERE status = 'SUCCESS';

CREATE TABLE reviews (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id  BIGINT      NOT NULL REFERENCES bookings (id),
    customer_id BIGINT      NOT NULL REFERENCES users (id),
    venue_id    BIGINT      NOT NULL REFERENCES venues (id),
    rating      SMALLINT    NOT NULL,
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_reviews_booking UNIQUE (booking_id),
    CONSTRAINT ck_reviews_rating  CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_reviews_venue ON reviews (venue_id, created_at DESC);
