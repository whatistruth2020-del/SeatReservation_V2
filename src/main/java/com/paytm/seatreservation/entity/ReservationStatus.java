package com.paytm.seatreservation.entity;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    IDEMPOTENT_REPLAY,
    CANCELLED
}