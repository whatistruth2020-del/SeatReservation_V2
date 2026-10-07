package com.paytm.seatreservation.service;

import com.paytm.seatreservation.config.ReservationMetrics;
import com.paytm.seatreservation.entity.ReservationStatus;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationSeat;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.UserBookingLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldExpiryService {

    private final SeatRepository seatRepository;
    private final UserBookingLimitRepository userBookingLimitRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationMetrics metrics;

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void expireHolds() {

        OffsetDateTime now = OffsetDateTime.now();

        List<Reservation> expiredReservations =
                reservationRepository.findExpiredHolds(
                        ReservationStatus.HELD,
                        now
                );

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info(
            "Found {} expired reservations",
            expiredReservations.size()
        );

        for (Reservation reservation : expiredReservations) {

            try {
                expireReservation(reservation);

            } catch (Exception e) {
                log.warn(
                    "Failed to expire reservation {}: {}",
                    reservation.getReservationId(),
                    e.getMessage(),
                    e
                );
            }
        }
    }
    
    @Transactional
    public void expireReservation(Reservation reservation) {

        OffsetDateTime now = OffsetDateTime.now();

        // Double-check reservation is still HELD
        // and actually expired.
        if (reservation.getStatus() != ReservationStatus.HELD) {
            return;
        }

        if (reservation.getExpiresAt() == null ||
            reservation.getExpiresAt().isAfter(now)) {
            return;
        }

        Long userId = reservation.getUserId();
        Long showId = reservation.getShowId();

        for (ReservationSeat reservationSeat :
                reservation.getReservationSeats()) {

            Seat seat = reservationSeat.getSeat();

            if (seat.getStatus() != SeatStatus.HELD) {
                continue;
            }

            // Make sure this seat belongs to this reservation/user
            if (!userId.equals(seat.getUserId())) {
                continue;
            }

            Long seatId = seat.getId();
            Long version = seat.getVersion();

            int updated = seatRepository.releaseSeatWithVersion(
                    seatId,
                    version,
                    SeatStatus.HELD,
                    SeatStatus.AVAILABLE
            );

            if (updated > 0) {
                log.debug(
                    "Released seat {} for expired reservation {}",
                    seat.getSeatNumber(),
                    reservation.getReservationId()
                );
            }
        }

        // Now expire the reservation
        reservation.setStatus(ReservationStatus.EXPIRED);

        reservationRepository.save(reservation);

        // Decrement user's held count
        userBookingLimitRepository
            .findByUserIdAndShowId(userId, showId)
            .ifPresent(limit -> {

                int numberOfSeats = reservation
                        .getReservationSeats()
                        .size();

                limit.setTicketsHeld(
                    Math.max(
                        0,
                        limit.getTicketsHeld() - numberOfSeats
                    )
                );

                userBookingLimitRepository.save(limit);
            });

        // Update available seat metric
        long available =
                seatRepository.countByShowIdAndStatus(
                    showId,
                    SeatStatus.AVAILABLE
                );

        metrics.updateSeatsAvailable(showId, available);

        log.info(
            "Expired reservation {} and released {} seats",
            reservation.getReservationId(),
            reservation.getReservationSeats().size()
        );
    }

    @Transactional
    public void expireSeat(Seat seat) {
        // Double-check the hold is still expired
        if (seat.getHoldExpiresAt() == null || seat.getHoldExpiresAt().isAfter(OffsetDateTime.now())) {
            return;
        }

        Long userId = seat.getUserId();
        Long showId = seat.getShow().getId();
        Long seatId = seat.getId();
        Long version = seat.getVersion();

        // Attempt to release the seat with optimistic locking
        int updated = seatRepository.releaseSeatWithVersion(
                seatId, version, SeatStatus.HELD, SeatStatus.AVAILABLE);

        if (updated == 0) {
            // Seat was already modified by another transaction
            return;
        }

        // Decrement user's held count
        userBookingLimitRepository.findByUserIdAndShowId(userId, showId).ifPresent(limit -> {
            limit.setTicketsHeld(Math.max(0, limit.getTicketsHeld() - 1));
            userBookingLimitRepository.save(limit);
        });

        // Update metrics
        long available = seatRepository.countByShowIdAndStatus(showId, SeatStatus.AVAILABLE);
        metrics.updateSeatsAvailable(showId, available);

        log.debug("Expired hold for seat {} in show {}", seat.getSeatNumber(), showId);
    }
}