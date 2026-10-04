package com.paytm.seatreservation.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfirmResponse {
    private UUID reservationId;
    private Long showId;
    private Long userId;
    private List<String> seats;
    private Long amountPaise;
    private String status;
    private OffsetDateTime confirmedAt;
}