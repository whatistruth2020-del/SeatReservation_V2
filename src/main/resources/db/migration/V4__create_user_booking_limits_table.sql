-- V4__create_user_booking_limits_table.sql
CREATE TABLE user_booking_limits (
    user_id BIGINT NOT NULL,
    show_id BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    tickets_held INTEGER NOT NULL DEFAULT 0 CHECK (tickets_held >= 0),
    tickets_confirmed INTEGER NOT NULL DEFAULT 0 CHECK (tickets_confirmed >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, show_id)
);

CREATE INDEX idx_user_booking_limits_show_id ON user_booking_limits(show_id);