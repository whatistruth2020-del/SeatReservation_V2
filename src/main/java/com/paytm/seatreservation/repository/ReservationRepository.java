package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    Optional<Reservation> findById(String idempotencyKey);

    List<Reservation> findByShowIdAndUserId(Long showId, Long userId);

    @Query("SELECT r FROM Reservation r WHERE r.showId = :showId AND r.status = :status")
    List<Reservation> findByShowIdAndStatus(Long showId, ReservationStatus status);
}