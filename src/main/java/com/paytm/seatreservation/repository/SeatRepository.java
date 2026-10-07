package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.LockModeType;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByShowIdOrderBySeatNumber(Long showId);

    List<Seat> findByShowIdAndStatus(Long showId, SeatStatus status);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.show.id = :showId AND s.status = :status")
    long countByShowIdAndStatus(Long showId, SeatStatus status);

    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers")
    List<Seat> findByShowIdAndSeatNumbers(Long showId, List<String> seatNumbers);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Seat s
        SET s.status = :newStatus,
            s.userId = :userId,
            s.holdExpiresAt = :holdExpiresAt,
            s.version = s.version + 1
        WHERE s.id = :seatId
          AND s.version = :expectedVersion
          AND s.status = :expectedStatus
        """)
    int updateSeatStatusWithVersion(
            Long seatId,
            Long expectedVersion,
            SeatStatus expectedStatus,
            SeatStatus newStatus,
            Long userId,
            java.time.OffsetDateTime holdExpiresAt);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Seat s
        SET s.status = :newStatus,
            s.userId = NULL,
            s.holdExpiresAt = NULL,
            s.version = s.version + 1
        WHERE s.id = :seatId
          AND s.version = :expectedVersion
          AND s.status = :expectedStatus
        """)
    int releaseSeatWithVersion(
            Long seatId,
            Long expectedVersion,
            SeatStatus expectedStatus,
            SeatStatus newStatus);

    @Modifying
    @Query("""
        UPDATE Seat s
           SET s.status = :newStatus,
               s.holdExpiresAt = NULL,
               s.updatedAt = CURRENT_TIMESTAMP,
               s.version = s.version + 1
         WHERE s.id = :seatId
           AND s.version = :version
           AND s.status = :oldStatus
    """)
    int confirmSeat(
             Long seatId,
             Long version,
             SeatStatus oldStatus,
            SeatStatus newStatus);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers ORDER BY s.seatNumber")
    List<Seat> findByShowIdAndSeatNumbersForUpdate(Long showId, List<String> seatNumbers);

    @Query("SELECT s FROM Seat s WHERE s.status = :status AND s.holdExpiresAt IS NOT NULL AND s.holdExpiresAt <= :now")
    List<Seat> findExpiredHolds(SeatStatus status, OffsetDateTime now);
}