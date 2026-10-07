-- 1. Make reservation_id the primary key

ALTER TABLE reservations
DROP CONSTRAINT reservations_pkey;

ALTER TABLE reservations
ALTER COLUMN reservation_id SET NOT NULL;

ALTER TABLE reservations
ADD CONSTRAINT reservations_pkey
PRIMARY KEY (reservation_id);


-- 2. Keep idempotency_key unique

ALTER TABLE reservations
ADD CONSTRAINT uk_reservations_idempotency_key
UNIQUE (idempotency_key);


-- 3. Create relationship between reservations and seats

CREATE TABLE reservation_seats (
    id BIGSERIAL PRIMARY KEY,

    reservation_id UUID NOT NULL,
    seat_id BIGINT NOT NULL,

    CONSTRAINT fk_reservation_seat_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES reservations(reservation_id),

    CONSTRAINT fk_reservation_seat_seat
        FOREIGN KEY (seat_id)
        REFERENCES seats(id),

    CONSTRAINT uk_reservation_seat
        UNIQUE (reservation_id, seat_id)
);


-- 4. Indexes

CREATE INDEX idx_reservation_seats_reservation_id
ON reservation_seats(reservation_id);

CREATE INDEX idx_reservation_seats_seat_id
ON reservation_seats(seat_id);