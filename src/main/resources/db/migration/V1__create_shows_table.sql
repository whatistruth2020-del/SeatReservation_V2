-- V1__create_shows_table.sql
CREATE TABLE shows (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    price_paise BIGINT NOT NULL CHECK (price_paise > 0),
    per_user_limit INTEGER NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
    total_seats INTEGER NOT NULL CHECK (total_seats > 0),
    sale_opens_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_shows_name ON shows(name);
CREATE INDEX idx_shows_sale_opens_at ON shows(sale_opens_at);