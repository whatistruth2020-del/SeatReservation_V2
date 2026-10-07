package com.paytm.seatreservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.reservation")
@Validated
public class AppProperties {

    @NotNull
    @Min(1)
    @Max(20)
    private Integer defaultPerUserLimit = 4;

    @NotNull
    @Min(1)
    @Max(60)
    private Integer holdTtlMinutes = 1;

    @NotNull
    @Min(1)
    @Max(168)
    private Integer idempotencyTtlHours = 24;

    @NotNull
    @Min(100)
    @Max(30000)
    private Integer lockTimeoutMs = 5000;

    @NotNull
    @Min(1)
    @Max(50)
    private Integer maxSeatsPerRequest = 4;

    public Integer getDefaultPerUserLimit() {
        return defaultPerUserLimit;
    }

    public void setDefaultPerUserLimit(Integer defaultPerUserLimit) {
        this.defaultPerUserLimit = defaultPerUserLimit;
    }

    public Integer getHoldTtlMinutes() {
        return holdTtlMinutes;
    }

    public void setHoldTtlMinutes(Integer holdTtlMinutes) {
        this.holdTtlMinutes = holdTtlMinutes;
    }

    public Integer getIdempotencyTtlHours() {
        return idempotencyTtlHours;
    }

    public void setIdempotencyTtlHours(Integer idempotencyTtlHours) {
        this.idempotencyTtlHours = idempotencyTtlHours;
    }

    public Integer getLockTimeoutMs() {
        return lockTimeoutMs;
    }

    public void setLockTimeoutMs(Integer lockTimeoutMs) {
        this.lockTimeoutMs = lockTimeoutMs;
    }

    public Integer getMaxSeatsPerRequest() {
        return maxSeatsPerRequest;
    }

    public void setMaxSeatsPerRequest(Integer maxSeatsPerRequest) {
        this.maxSeatsPerRequest = maxSeatsPerRequest;
    }

    public Duration getHoldTtl() {
        return Duration.ofMinutes(holdTtlMinutes);
    }

    public Duration getIdempotencyTtl() {
        return Duration.ofHours(idempotencyTtlHours);
    }
}