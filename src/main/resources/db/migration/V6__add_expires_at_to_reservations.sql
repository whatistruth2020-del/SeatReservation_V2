ALTER TABLE reservations
ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_reservations_status_expires_at
ON reservations (status, expires_at);