ALTER TABLE reservations
DROP CONSTRAINT reservations_status_check;

ALTER TABLE reservations
ADD CONSTRAINT reservations_status_check
CHECK (
    status IN (
        'PENDING',
        'HELD',
        'CONFIRMED',
        'FAILED',
        'IDEMPOTENT_REPLAY',
        'CANCELLED'
    )
);