package com.paytm.seatreservation.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatInfo {

    private String seatNumber;
    private String status;
}