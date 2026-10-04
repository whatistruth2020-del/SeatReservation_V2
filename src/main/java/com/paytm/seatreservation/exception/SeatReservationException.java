package com.paytm.seatreservation.exception;

public class SeatReservationException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public SeatReservationException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static SeatReservationException seatTaken(String seatNumber) {
        return new SeatReservationException("SEAT_TAKEN", "Seat " + seatNumber + " is already taken", 409);
    }

    public static SeatReservationException userLimitExceeded(int limit) {
        return new SeatReservationException("USER_LIMIT_EXCEEDED", "User cannot hold more than " + limit + " seats for this show", 409);
    }

    public static SeatReservationException idempotencyConflict(String key) {
        return new SeatReservationException("IDEMPOTENCY_CONFLICT", "Idempotency key " + key + " already used with different request", 409);
    }

    public static SeatReservationException idempotentReplay() {
        return new SeatReservationException("IDEMPOTENT_REPLAY", "Request already processed", 409);
    }

    public static SeatReservationException showNotFound(Long showId) {
        return new SeatReservationException("SHOW_NOT_FOUND", "Show not found: " + showId, 404);
    }

    public static SeatReservationException seatNotFound(String seatNumber) {
        return new SeatReservationException("SEAT_NOT_FOUND", "Seat not found: " + seatNumber, 404);
    }

    public static SeatReservationException invalidSeatStatus(String seatNumber, String status) {
        return new SeatReservationException("INVALID_SEAT_STATUS", "Seat " + seatNumber + " has invalid status: " + status, 409);
    }

    public static SeatReservationException tooManySeats(int max) {
        return new SeatReservationException("TOO_MANY_SEATS", "Cannot reserve more than " + max + " seats at once", 400);
    }

    public static SeatReservationException holdExpired(String seatNumber) {
        return new SeatReservationException("HOLD_EXPIRED", "Hold on seat " + seatNumber + " has expired", 409);
    }

    public static SeatReservationException unauthorized(String message) {
        return new SeatReservationException("UNAUTHORIZED", message, 401);
    }

    public static SeatReservationException internalError(String message) {
        return new SeatReservationException("INTERNAL_ERROR", message, 500);
    }
}