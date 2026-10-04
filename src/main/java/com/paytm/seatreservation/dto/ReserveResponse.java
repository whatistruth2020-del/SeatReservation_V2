package com.paytm.seatreservation.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveResponse {

    private UUID reservationId;
    private Long showId;
    private Long userId;
    private List<String> seats;
    private Long amountPaise;
    private String status;
}