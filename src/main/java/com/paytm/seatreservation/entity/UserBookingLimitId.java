package com.paytm.seatreservation.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserBookingLimitId implements Serializable {

    private Long userId;
    private Long showId;
}