package com.paytm.seatreservation.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowStateResponse {

    private Long id;
    private String name;
    private Long pricePaise;
    private Integer perUserLimit;
    private Integer totalSeats;
    private Integer availableSeats;
    private Integer heldSeats;
    private Integer confirmedSeats;
    private List<SeatInfo> seats;
    private Map<String, Long> statusCounts;
}