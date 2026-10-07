package com.paytm.seatreservation.controller;

import com.paytm.seatreservation.dto.ConfirmResponse;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.dto.ReserveResponse;
import com.paytm.seatreservation.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReservationController {

    private final BookingService bookingService;

    @PostMapping("/shows/{id}/reserve")
    public ResponseEntity<ReserveResponse> reserveSeats(
            @PathVariable Long id,
            @Valid @RequestBody ReserveRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String headerIdempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false)
            String userIdHeader) {

        Long userId = parseUserId(userIdHeader);

        String idempotencyKey =
                resolveIdempotencyKey(
                        headerIdempotencyKey,
                        request.getIdempotencyKey()
                );

        ReserveResponse response =
                bookingService.reserveSeats(
                        id,
                        userId,
                        request,
                        idempotencyKey
                );

        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<ConfirmResponse> confirmReservation(
            @PathVariable UUID reservationId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false)
            String userIdHeader) {

        Long userId = parseUserId(userIdHeader);

        String key = resolveIdempotencyKey(
                idempotencyKey,
                null
        );

        ConfirmResponse response =
                bookingService.confirmReservation(
                        reservationId,
                        userId,
                        key
                );

        return ResponseEntity.ok(response);
    }

    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new IllegalArgumentException(
                    "X-User-Id header is required"
            );
        }

        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid X-User-Id header: " + userIdHeader
            );
        }
    }

    private String resolveIdempotencyKey(
            String headerKey,
            String bodyKey) {

        String key = headerKey != null ? headerKey : bodyKey;

        if (key == null || key.isBlank()) {
            key = UUID.randomUUID().toString();
        }

        return key;
    }
}