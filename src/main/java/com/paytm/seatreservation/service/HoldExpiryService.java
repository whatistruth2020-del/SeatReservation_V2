package com.paytm.seatreservation.service;

import com.paytm.seatreservation.config.ReservationMetrics;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
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
    private final ReservationMetrics metrics;

    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    @Transactional
    public void expireHolds() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Seat> expiredSeats = seatRepository.findExpiredHolds(now);

        if (expiredSeats.isEmpty()) {
            return;
        }

        log.info("Expiring {} holds", expiredSeats.size());

        for (Seat seat : expiredSeats) {
            try {
                expireSeat(seat);
            } catch (Exception e) {
                log.warn("Failed to expire hold for seat {}: {}", seat.getSeatNumber(), e.getMessage());
            }
        }
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