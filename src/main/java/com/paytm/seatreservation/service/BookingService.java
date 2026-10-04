package com.paytm.seatreservation.service;

import com.paytm.seatreservation.config.AppProperties;
import com.paytm.seatreservation.config.ReservationMetrics;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.dto.ReserveResponse;
import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationStatus;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.entity.UserBookingLimit;
import com.paytm.seatreservation.exception.SeatReservationException;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import com.paytm.seatreservation.repository.UserBookingLimitRepository;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final UserBookingLimitRepository userBookingLimitRepository;
    private final RedisLockService redisLockService;
    private final IdempotencyService idempotencyService;
    private final AppProperties appProperties;
    private final ReservationMetrics metrics;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 10, multiplier = 2)
    )
    public ReserveResponse reserveSeats(Long showId, Long userId, ReserveRequest request, String idempotencyKey) {
        Timer.Sample reservationTimer = metrics.startReservationTimer();
        Timer.Sample lockTimer = metrics.startLockAcquireTimer();
        Timer.Sample dbTimer = metrics.startDbTransactionTimer();

        try {
            // 1. Check idempotency cache first
            Optional<ReserveResponse> cached = idempotencyService.getCachedResponse(idempotencyKey);
            if (cached.isPresent()) {
                metrics.incrementDeclinedIdempotentReplay();
                metrics.stopReservationTimer(reservationTimer);
                log.info("Idempotent replay for key: {}", idempotencyKey);
                return cached.get();
            }

            // 2. Try to mark as pending in Redis (prevents concurrent processing of same key)
            if (!idempotencyService.tryMarkPending(idempotencyKey, appProperties.getIdempotencyTtl())) {
                // Another request with same key is being processed, wait and check cache
                Optional<ReserveResponse> pendingCached = waitForPending(idempotencyKey);
                if (pendingCached.isPresent()) {
                    metrics.incrementDeclinedIdempotentReplay();
                    metrics.stopReservationTimer(reservationTimer);
                    return pendingCached.get();
                }
                // If we get here, the pending request failed, we can proceed
            }

            try {
                // 3. Validate show exists
                Show show = showRepository.findById(showId)
                        .orElseThrow(() -> SeatReservationException.showNotFound(showId));

                // 4. Validate seat count
                if (request.getSeats().size() > appProperties.getMaxSeatsPerRequest()) {
                    throw SeatReservationException.tooManySeats(appProperties.getMaxSeatsPerRequest());
                }

                // 5. Check user limit (with pessimistic lock)
                UserBookingLimit userLimit = checkAndIncrementUserLimit(showId, userId, request.getSeats().size(), show.getPerUserLimit());

                // 6. Acquire Redis locks for seats (sorted to prevent deadlock)
                List<String> acquiredLocks = redisLockService.acquireLocks(
                        showId, request.getSeats(), appProperties.getHoldTtl());
                metrics.stopLockAcquireTimer(lockTimer);

                if (acquiredLocks.isEmpty()) {
                    // Failed to acquire locks - seat(s) taken
                    rollbackUserLimit(userLimit, request.getSeats().size());
                    metrics.incrementDeclinedSeatTaken();
                    metrics.stopReservationTimer(reservationTimer);
                    throw SeatReservationException.seatTaken("one or more requested seats");
                }

                try {
                    // 7. Execute atomic DB transaction with optimistic locking
                    ReserveResponse response = executeReservationTransaction(
                            show, userId, request, idempotencyKey, acquiredLocks, userLimit);
                    metrics.stopDbTransactionTimer(dbTimer);

                    // 8. Cache successful response
                    idempotencyService.cacheResponse(idempotencyKey, response, appProperties.getIdempotencyTtl());
                    idempotencyService.removePending(idempotencyKey);

                    // 9. Update metrics
                    metrics.incrementConfirmed();
                    updateSeatsAvailableMetric(showId);

                    metrics.stopReservationTimer(reservationTimer);
                    return response;

                } catch (SeatReservationException e) {
                    // Release Redis locks on failure
                    redisLockService.releaseLocks(acquiredLocks, getLockValue(acquiredLocks));
                    rollbackUserLimit(userLimit, request.getSeats().size());
                    throw e;
                }

            } catch (SeatReservationException e) {
                idempotencyService.removePending(idempotencyKey);
                throw e;
            }

        } catch (SeatReservationException e) {
            metrics.stopReservationTimer(reservationTimer);
            throw e;
        } catch (Exception e) {
            metrics.incrementServerError();
            metrics.stopReservationTimer(reservationTimer);
            log.error("Unexpected error during reservation", e);
            throw SeatReservationException.internalError("Reservation failed: " + e.getMessage());
        }
    }

    private UserBookingLimit checkAndIncrementUserLimit(Long showId, Long userId, int requestedSeats, int perUserLimit) {
        UserBookingLimit userLimit = userBookingLimitRepository.findByUserIdAndShowIdForUpdate(userId, showId)
                .orElseGet(() -> UserBookingLimit.builder()
                        .userId(userId)
                        .showId(showId)
                        .ticketsHeld(0)
                        .ticketsConfirmed(0)
                        .build());

        int newTotal = userLimit.getTotalTickets() + requestedSeats;
        if (newTotal > perUserLimit) {
            throw SeatReservationException.userLimitExceeded(perUserLimit);
        }

        userLimit.setTicketsHeld(userLimit.getTicketsHeld() + requestedSeats);
        return userBookingLimitRepository.save(userLimit);
    }

    private void rollbackUserLimit(UserBookingLimit userLimit, int requestedSeats) {
        try {
            userLimit.setTicketsHeld(Math.max(0, userLimit.getTicketsHeld() - requestedSeats));
            userBookingLimitRepository.save(userLimit);
        } catch (Exception e) {
            log.warn("Failed to rollback user limit", e);
        }
    }

    private ReserveResponse executeReservationTransaction(
            Show show, Long userId, ReserveRequest request, String idempotencyKey,
            List<String> acquiredLocks, UserBookingLimit userLimit) {

        // Re-fetch seats with pessimistic lock in sorted order
        List<Seat> seats = seatRepository.findByShowIdAndSeatNumbersForUpdate(show.getId(), request.getSeats());

        if (seats.size() != request.getSeats().size()) {
            throw SeatReservationException.seatNotFound("one or more requested seats");
        }

        // Verify all seats are available
        for (Seat seat : seats) {
            if (!seat.isAvailable()) {
                if (seat.isHeld() && seat.isExpired()) {
                    // Expired hold - can be taken
                    continue;
                }
                throw SeatReservationException.seatTaken(seat.getSeatNumber());
            }
        }

        // All seats available - confirm them
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime holdExpiresAt = now.plus(appProperties.getHoldTtl());

        for (Seat seat : seats) {
            int updated = seatRepository.updateSeatStatusWithVersion(
                    seat.getId(),
                    seat.getVersion(),
                    SeatStatus.AVAILABLE,
                    SeatStatus.HELD,
                    userId,
                    holdExpiresAt
            );
            if (updated == 0) {
                // Race condition - seat was taken by another transaction
                throw SeatReservationException.seatTaken(seat.getSeatNumber());
            }
        }

        // Create reservation record
        UUID reservationId = UUID.randomUUID();
        Long amountPaise = show.getPricePaise() * request.getSeats().size();

        Reservation reservation = Reservation.builder()
                .idempotencyKey(idempotencyKey)
                .showId(show.getId())
                .userId(userId)
                .seatNumbers(request.getSeats().toArray(new String[0]))
                .amountPaise(amountPaise)
                .status(ReservationStatus.CONFIRMED)
                .reservationId(reservationId)
                .confirmedAt(now)
                .build();

        reservationRepository.save(reservation);

        // Update user limit: move from held to confirmed
        userLimit.setTicketsHeld(userLimit.getTicketsHeld() - request.getSeats().size());
        userLimit.setTicketsConfirmed(userLimit.getTicketsConfirmed() + request.getSeats().size());
        userBookingLimitRepository.save(userLimit);

        log.info("Reservation confirmed: id={}, user={}, seats={}", reservationId, userId, request.getSeats());

        return ReserveResponse.builder()
                .reservationId(reservationId)
                .showId(show.getId())
                .userId(userId)
                .seats(request.getSeats())
                .amountPaise(amountPaise)
                .status("confirmed")
                .build();
    }

    private Optional<ReserveResponse> waitForPending(String idempotencyKey) {
        // Simple polling - in production could use Redis pub/sub
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(100);
                Optional<ReserveResponse> cached = idempotencyService.getCachedResponse(idempotencyKey);
                if (cached.isPresent()) {
                    return cached;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Optional.empty();
    }

    private String getLockValue(List<String> acquiredLocks) {
        // In a real implementation, we'd store the lock value
        // For now, we'll just release without value check (less safe)
        return "";
    }

    private void updateSeatsAvailableMetric(Long showId) {
        long available = seatRepository.countByShowIdAndStatus(showId, SeatStatus.AVAILABLE);
        metrics.updateSeatsAvailable(showId, available);
    }

    @Transactional
    public void expireHolds() {
        // This is better handled by a scheduled job that finds all expired holds
        // Implementation would use a custom query to find expired seats
    }
}