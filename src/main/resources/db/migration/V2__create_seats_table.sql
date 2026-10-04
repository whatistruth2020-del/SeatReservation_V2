-- V2__create_seats_table.sql
CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),
    user_id BIGINT,
    hold_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_show_seat UNIQUE (show_id, seat_number)
);

CREATE INDEX idx_seats_show_id ON seats(show_id);
CREATE INDEX idx_seats_show_id_status ON seats(show_id, status);
CREATE INDEX idx_seats_user_id ON seats(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_seats_hold_expires_at ON seats(hold_expires_at) WHERE hold_expires_at IS NOT NULL;