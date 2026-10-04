package com.paytm.seatreservation.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShowRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    @Size(min = 1, max = 10000)
    private List<@NotBlank @Size(max = 50) String> seats;

    @NotNull
    @Min(1)
    private Long pricePaise;
}