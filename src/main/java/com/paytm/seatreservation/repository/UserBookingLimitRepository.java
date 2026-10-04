package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.UserBookingLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

@Repository
public interface UserBookingLimitRepository extends JpaRepository<UserBookingLimit, com.paytm.seatreservation.entity.UserBookingLimitId> {

    Optional<UserBookingLimit> findByUserIdAndShowId(Long userId, Long showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserBookingLimit u WHERE u.userId = :userId AND u.showId = :showId")
    Optional<UserBookingLimit> findByUserIdAndShowIdForUpdate(Long userId, Long showId);
}