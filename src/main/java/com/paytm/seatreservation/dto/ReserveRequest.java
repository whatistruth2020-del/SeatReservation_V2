package com.paytm.seatreservation.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveRequest {

    @NotNull
    @Size(min = 1, max = 50)
    private List<@NotBlank @Size(max = 50) String> seats;

    @Size(max = 255)
    private String idempotencyKey;
}