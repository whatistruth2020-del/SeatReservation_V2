package com.paytm.seatreservation.repository;


import com.paytm.seatreservation.entity.ReservationSeat;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;


@Repository
public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {


}