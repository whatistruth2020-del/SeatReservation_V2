package com.paytm.seatreservation.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShowResponse {

    private Long id;
    private String name;
    private Long pricePaise;
    private Integer perUserLimit;
    private Integer totalSeats;
    private List<SeatInfo> seats;
}