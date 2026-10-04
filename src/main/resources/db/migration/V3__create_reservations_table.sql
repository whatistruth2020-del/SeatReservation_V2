-- V3__create_reservations_table.sql
CREATE TABLE reservations (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    seat_numbers TEXT[] NOT NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'IDEMPOTENT_REPLAY', 'CANCELLED')),
    failure_reason VARCHAR(100),
    reservation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX idx_reservations_show_id ON reservations(show_id);
CREATE INDEX idx_reservations_user_id ON reservations(user_id);
CREATE INDEX idx_reservations_status ON reservations(status);