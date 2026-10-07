package com.paytm.seatreservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.seatreservation.dto.ReserveResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "idempotency:";
    private static final String PENDING_PREFIX = "idempotency:pending:";

    private String buildCacheKey(String operation, String idempotencyKey) {
        return CACHE_PREFIX + operation + ":" + idempotencyKey;
    }

    private String buildPendingKey(String operation, String idempotencyKey) {
        return PENDING_PREFIX + operation + ":" + idempotencyKey;
    }

    // ---------------------------------------------------------
    // CACHE RESPONSE
    // ---------------------------------------------------------

    public <T> void cacheResponse(
            String operation,
            String idempotencyKey,
            T response,
            Duration ttl) {

        String key = buildCacheKey(operation, idempotencyKey);

        redisTemplate.opsForValue().set(
                key,
                response,
                ttl);
    }

    // ---------------------------------------------------------
    // GET CACHED RESPONSE
    // ---------------------------------------------------------

    public <T> Optional<T> getCachedResponse(
            String operation,
            String idempotencyKey,
            Class<T> responseType) {

        String key = buildCacheKey(operation, idempotencyKey);

        Object value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(responseType.cast(value));
    }

    // ---------------------------------------------------------
    // MARK REQUEST AS PENDING
    // ---------------------------------------------------------

    public boolean tryMarkPending(
            String operation,
            String idempotencyKey,
            Duration ttl) {

        String key = buildPendingKey(operation, idempotencyKey);

        Boolean created = redisTemplate.opsForValue()
                .setIfAbsent(key, "PROCESSING", ttl);

        return Boolean.TRUE.equals(created);
    }

    // ---------------------------------------------------------
    // REMOVE PENDING
    // ---------------------------------------------------------

    public void removePending(
            String operation,
            String idempotencyKey) {

        String key = buildPendingKey(operation, idempotencyKey);

        redisTemplate.delete(key);
    }
    
    
}