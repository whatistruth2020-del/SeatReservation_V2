package com.paytm.seatreservation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_booking_limits")
@IdClass(UserBookingLimitId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBookingLimit {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "tickets_held", nullable = false)
    private Integer ticketsHeld = 0;

    @Column(name = "tickets_confirmed", nullable = false)
    private Integer ticketsConfirmed = 0;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public int getTotalTickets() {
        return ticketsHeld + ticketsConfirmed;
    }
}