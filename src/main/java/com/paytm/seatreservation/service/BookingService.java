package com.paytm.seatreservation.service;

import com.paytm.seatreservation.config.AppProperties;
import com.paytm.seatreservation.config.ReservationMetrics;
import com.paytm.seatreservation.dto.ConfirmResponse;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.dto.ReserveResponse;
import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationSeat;
import com.paytm.seatreservation.entity.ReservationStatus;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.entity.UserBookingLimit;
import com.paytm.seatreservation.exception.SeatReservationException;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.ReservationSeatRepository;
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
    private final ReservationSeatRepository reservationSeatRepository;
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
    public ReserveResponse reserveSeats(
            Long showId,
            Long userId,
            ReserveRequest request,
            String idempotencyKey) {

        Timer.Sample reservationTimer =
                metrics.startReservationTimer();

        Timer.Sample lockTimer =
                metrics.startLockAcquireTimer();

        Timer.Sample dbTimer =
                metrics.startDbTransactionTimer();

        try {

            // =====================================================
            // 1. CHECK COMPLETED RESPONSE FIRST
            // =====================================================

            Optional<ReserveResponse> cached =
                    idempotencyService.getCachedResponse(
                            "reserve",
                            idempotencyKey,
                            ReserveResponse.class);

            if (cached.isPresent()) {

                metrics.incrementDeclinedIdempotentReplay();

                metrics.stopReservationTimer(reservationTimer);

                log.info(
                        "Idempotent replay for reserve key: {}",
                        idempotencyKey);

                return cached.get();
            }


            // =====================================================
            // 2. MARK REQUEST AS PENDING
            // =====================================================

            if (!idempotencyService.tryMarkPending(
                    "reserve",
                    idempotencyKey,
                    appProperties.getIdempotencyTtl())) {

                // Another request with the same key is processing

                Optional<ReserveResponse> pendingCached =
                		waitForPending(
                                "reserve",
                                idempotencyKey,
                                ReserveResponse.class
                        );

                if (pendingCached.isPresent()) {

                    metrics.incrementDeclinedIdempotentReplay();

                    metrics.stopReservationTimer(reservationTimer);

                    return pendingCached.get();
                }
            }


            RedisLockService.AcquiredLocks lockResult = null;

            try {

                // =================================================
                // 3. VALIDATE SHOW
                // =================================================

                Show show = showRepository.findById(showId)
                        .orElseThrow(() ->
                                SeatReservationException
                                        .showNotFound(showId));


                // =================================================
                // 4. VALIDATE SEAT COUNT
                // =================================================

                if (request.getSeats().size()
                        > appProperties.getMaxSeatsPerRequest()) {

                    throw SeatReservationException.tooManySeats(
                            appProperties.getMaxSeatsPerRequest());
                }


                // =================================================
                // 5. CHECK USER BOOKING LIMIT
                // =================================================

                UserBookingLimit userLimit =
                        checkAndIncrementUserLimit(
                                showId,
                                userId,
                                request.getSeats().size(),
                                show.getPerUserLimit());


                // =================================================
                // 6. ACQUIRE REDIS SEAT LOCKS
                // =================================================

                lockResult = redisLockService.acquireLocks(
                        showId,
                        request.getSeats(),
                        appProperties.getHoldTtl());

                metrics.stopLockAcquireTimer(lockTimer);

                if (lockResult == null) {

                    rollbackUserLimit(
                            userLimit,
                            request.getSeats().size());

                    metrics.incrementDeclinedSeatTaken();

                    throw SeatReservationException.seatTaken(
                            "one or more requested seats");
                }


                // =================================================
                // 7. DATABASE TRANSACTION
                // =================================================

                ReserveResponse response =
                        executeReservationTransaction(
                                show,
                                userId,
                                request,
                                idempotencyKey,
                                lockResult.getKeys(),
                                userLimit);

                metrics.stopDbTransactionTimer(dbTimer);


                // =================================================
                // 8. CACHE SUCCESSFUL RESPONSE
                // =================================================

                idempotencyService.cacheResponse(
                        "reserve",
                        idempotencyKey,
                        response,
                        appProperties.getIdempotencyTtl());


                // =================================================
                // 9. REMOVE PENDING
                // =================================================

                idempotencyService.removePending(
                        "reserve",
                        idempotencyKey);


                metrics.incrementConfirmed();

                updateSeatsAvailableMetric(showId);

                metrics.stopReservationTimer(reservationTimer);

                return response;


            } catch (SeatReservationException e) {

                // DB failed / business failure

                idempotencyService.removePending(
                        "reserve",
                        idempotencyKey);

              //  rollbackUserLimitIfNecessary(e);

                throw e;

            } finally {

                // =================================================
                // 10. ALWAYS RELEASE REDIS SEAT LOCK
                // =================================================

                if (lockResult != null) {

                    redisLockService.releaseLocks(
                            lockResult.getKeys(),
                            lockResult.getLockValue());
                }
            }


        } catch (SeatReservationException e) {

            metrics.stopReservationTimer(reservationTimer);

            throw e;


        } catch (Exception e) {

            metrics.incrementServerError();

            metrics.stopReservationTimer(reservationTimer);

            log.error(
                    "Unexpected error during reservation",
                    e);

            throw SeatReservationException.internalError(
                    "Reservation failed: " + e.getMessage());
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

     // Create reservation record in HELD state
       // UUID reservationId = UUID.randomUUID();
        Long amountPaise = show.getPricePaise() * request.getSeats().size();

        Reservation reservation = Reservation.builder()
                .idempotencyKey(idempotencyKey)
                .showId(show.getId())
                .userId(userId)
                .amountPaise(amountPaise)
                .status(ReservationStatus.HELD)
                .expiresAt(holdExpiresAt)
                .build();

        for (Seat seat : seats) {

            ReservationSeat reservationSeat = ReservationSeat.builder()
                    .reservation(reservation)
                    .seat(seat)
                    .build();

            reservation.getReservationSeats().add(reservationSeat);
        }
        Reservation savedReservation = reservationRepository.save(reservation);
        //reservationRepository.save(reservation);
        
        // Keep the tickets in HELD state.
        // They will move to CONFIRMED only when /confirm is called.
        userBookingLimitRepository.save(userLimit);

        log.info("Reservation held: id={}, user={}, seats={}, expiresAt={}",
        		savedReservation.getReservationId(), request.getSeats(), holdExpiresAt);

        return ReserveResponse.builder()
                .reservationId(savedReservation.getReservationId())
                .showId(show.getId())
                .userId(userId)
                .seats(request.getSeats())
                .amountPaise(amountPaise)
                .status("HELD")
                .build();
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConfirmResponse confirmReservation(
            UUID reservationId,
            Long userId,
            String idempotencyKey) {

        Timer.Sample reservationTimer =
                metrics.startReservationTimer();

        try {

            // =====================================================
            // 1. CHECK CONFIRM IDEMPOTENCY CACHE
            // =====================================================

            Optional<ConfirmResponse> cached =
                    idempotencyService.getCachedResponse(
                            "confirm",
                            idempotencyKey,
                            ConfirmResponse.class);

            if (cached.isPresent()) {

                metrics.incrementDeclinedIdempotentReplay();

                log.info(
                        "Idempotent replay for confirm key: {}",
                        idempotencyKey);

                return cached.get();
            }


            // =====================================================
            // 2. MARK CONFIRM REQUEST AS PENDING
            // =====================================================

            if (!idempotencyService.tryMarkPending(
                    "confirm",
                    idempotencyKey,
                    appProperties.getIdempotencyTtl())) {

                Optional<ConfirmResponse> pendingCached =
                		 waitForPending(
                	                "confirm",
                	                idempotencyKey,
                	                ConfirmResponse.class
                	        );

                if (pendingCached.isPresent()) {

                    metrics.incrementDeclinedIdempotentReplay();

                    return pendingCached.get();
                }
                
                // The previous request may still be processing.
                throw SeatReservationException.internalError(
                        "Confirm request is already being processed"
                );
            }


            RedisLockService.AcquiredLocks lockResult = null;

            try {

                // =================================================
                // 3. LOAD RESERVATION WITH DB LOCK
                // =================================================

                Reservation reservation =
                        reservationRepository
                                .findByIdForUpdate(reservationId)
                                .orElseThrow(() ->
                                        SeatReservationException
                                                .reservationNotFound(
                                                        reservationId));


                // =================================================
                // 4. VERIFY USER
                // =================================================

                if (!reservation.getUserId().equals(userId)) {

                    throw SeatReservationException
                            .reservationNotFound(reservationId);
                }


                // =================================================
                // 5. VERIFY RESERVATION STATE
                // =================================================

                if (reservation.getStatus()
                        != ReservationStatus.HELD) {

                    throw SeatReservationException
                            .invalidReservationState(
                                    "Reservation is not HELD");
                }


                // =================================================
                // 6. VERIFY EXPIRY
                // =================================================

                OffsetDateTime now =
                        OffsetDateTime.now();

                if (reservation.getExpiresAt() == null
                        || !reservation.getExpiresAt()
                                .isAfter(now)) {

                    throw SeatReservationException
                            .reservationExpired(
                                    reservationId);
                }


                // =================================================
                // 7. GET SEATS FROM RESERVATION
                // =================================================

                List<String> seatNumbers =
                        reservation.getReservationSeats()
                                .stream()
                                .map(rs ->
                                        rs.getSeat()
                                          .getSeatNumber())
                                .sorted()
                                .toList();


                // =================================================
                // 8. ACQUIRE REDIS SEAT LOCKS
                // =================================================

                lockResult =
                        redisLockService.acquireLocks(
                                reservation.getShowId(),
                                seatNumbers,
                                appProperties.getHoldTtl());

                if (lockResult == null) {

                    throw SeatReservationException.seatTaken(
                            "one or more seats are locked");
                }


                // =================================================
                // 9. CONFIRM SEATS IN DATABASE
                // =================================================

                for (ReservationSeat reservationSeat :
                        reservation.getReservationSeats()) {

                    Seat seat =
                            reservationSeat.getSeat();

                    if (seat.getStatus()
                            != SeatStatus.HELD) {

                        throw SeatReservationException.seatTaken(
                                seat.getSeatNumber());
                    }

                    if (!userId.equals(
                            seat.getUserId())) {

                        throw SeatReservationException.seatTaken(
                                seat.getSeatNumber());
                    }

                    int updated =
                            seatRepository.confirmSeat(
                                    seat.getId(),
                                    seat.getVersion(),
                                    SeatStatus.HELD,
                                    SeatStatus.CONFIRMED);

                    if (updated != 1) {

                        throw SeatReservationException.seatTaken(
                                seat.getSeatNumber());
                    }
                }


                // =================================================
                // 10. CONFIRM RESERVATION
                // =================================================

                reservation.setStatus(
                        ReservationStatus.CONFIRMED);

                reservation.setConfirmedAt(now);

                reservationRepository.save(reservation);


                // =================================================
                // 11. UPDATE BOOKING COUNTERS
                // =================================================

                int seatCount =
                        reservation.getReservationSeats()
                                .size();

                // Your existing counter-update logic here.


                // =================================================
                // 12. BUILD RESPONSE
                // =================================================

                ConfirmResponse response =

                        ConfirmResponse.builder()
                                .reservationId(reservationId)
                                .showId(reservation.getShowId())
                                .userId(reservation.getUserId())
                                .amountPaise(reservation.getAmountPaise())
                                .status("CONFIRMED")
                                .build();


                // =================================================
                // 13. CACHE CONFIRM RESPONSE
                // =================================================

                idempotencyService.cacheResponse(
                        "confirm",
                        idempotencyKey,
                        response,
                        appProperties.getIdempotencyTtl());


                // =================================================
                // 14. REMOVE CONFIRM PENDING
                // =================================================

                idempotencyService.removePending(
                        "confirm",
                        idempotencyKey);


                return response;


            } catch (SeatReservationException e) {

                idempotencyService.removePending(
                        "confirm",
                        idempotencyKey);

                throw e;

            } finally {

                // =================================================
                // 15. RELEASE REDIS SEAT LOCK
                // =================================================

                if (lockResult != null) {

                    redisLockService.releaseLocks(
                            lockResult.getKeys(),
                            lockResult.getLockValue());
                }
            }


        } finally {

            metrics.stopReservationTimer(reservationTimer);
        }
    }
    
    
    
    private <T> Optional<T> waitForPending(
            String operation,
            String idempotencyKey,
            Class<T> responseType) {

        for (int i = 0; i < 10; i++) {

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }

            Optional<T> cached =
                    idempotencyService.getCachedResponse(
                            operation,
                            idempotencyKey,
                            responseType
                    );

            if (cached.isPresent()) {
                return cached;
            }
        }

        return Optional.empty();
    }
}