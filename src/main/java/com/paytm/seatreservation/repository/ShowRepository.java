package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    Optional<Show> findByName(String name);

    @Query("SELECT s FROM Show s WHERE s.id = :id")
    Optional<Show> findByIdWithSeats(Long id);
}