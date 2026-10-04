package com.paytm.seatreservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.seatreservation.dto.ReserveResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix = "idempotency:";

    public IdempotencyService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks if idempotency key exists and returns cached response if found.
     * Returns Optional.empty() if not found.
     */
    public Optional<ReserveResponse> getCachedResponse(String idempotencyKey) {
        try {
            String key = keyPrefix + idempotencyKey;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Optional.of(objectMapper.convertValue(cached, ReserveResponse.class));
            }
        } catch (Exception e) {
            // Log error but don't fail - fall through to DB check
        }
        return Optional.empty();
    }

    /**
     * Stores the response in Redis cache
     */
    public void cacheResponse(String idempotencyKey, ReserveResponse response, Duration ttl) {
        try {
            String key = keyPrefix + idempotencyKey;
            redisTemplate.opsForValue().set(key, response, ttl);
        } catch (Exception e) {
            // Log error but don't fail
        }
    }

    /**
     * Stores a pending marker to prevent concurrent processing of same key
     * Returns true if marker was set (first request), false if already exists
     */
    public boolean tryMarkPending(String idempotencyKey, Duration ttl) {
        String key = keyPrefix + idempotencyKey + ":pending";
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", ttl));
    }

    public void removePending(String idempotencyKey) {
        String key = keyPrefix + idempotencyKey + ":pending";
        redisTemplate.delete(key);
    }
}