package com.paytm.seatreservation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(name = "uk_show_seat", columnNames = {"show_id", "seat_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public boolean isHeld() {
        return status == SeatStatus.HELD;
    }

    public boolean isConfirmed() {
        return status == SeatStatus.CONFIRMED;
    }

    public boolean isHeldBy(Long userId) {
        return status == SeatStatus.HELD && this.userId != null && this.userId.equals(userId);
    }

    public boolean isExpired() {
        return holdExpiresAt != null && holdExpiresAt.isBefore(OffsetDateTime.now());
    }
}