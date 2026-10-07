package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationStatus;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;

import jakarta.persistence.LockModeType;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    

    List<Reservation> findByShowIdAndUserId(Long showId, Long userId);

    @Query("SELECT r FROM Reservation r WHERE r.showId = :showId AND r.status = :status")
    List<Reservation> findByShowIdAndStatus(Long showId, ReservationStatus status);

    @Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.expiresAt IS NOT NULL AND r.expiresAt <= :now")
	List<Reservation> findExpiredHolds(ReservationStatus status, OffsetDateTime now);
    
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM Reservation r
        WHERE r.reservationId = :reservationId
    """)
    Optional<Reservation> findByIdForUpdate(
             UUID reservationId);
}