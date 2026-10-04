package com.paytm.seatreservation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @GetMapping("/health/live")
    public Health liveness() {
        return Health.up().build();
    }

    @GetMapping("/health/ready")
    public Health readiness() {
        Health.Builder builder = Health.up();

        // Check database
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            builder.withDetail("database", "UP");
        } catch (DataAccessResourceFailureException | Exception e) {
            builder.withDetail("database", "DOWN: " + e.getMessage());
            return builder.status(Status.DOWN).build();
        }

        // Check Redis
        try {
            if (redisConnectionFactory != null) {
                redisConnectionFactory.getConnection().ping();
                builder.withDetail("redis", "UP");
            } else {
                builder.withDetail("redis", "NOT_CONFIGURED");
            }
        } catch (RedisConnectionFailureException | Exception e) {
            builder.withDetail("redis", "DOWN: " + e.getMessage());
            return builder.status(Status.DOWN).build();
        }

        return builder.build();
    }
}