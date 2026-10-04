package com.paytm.seatreservation.service;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class RedisLockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LOCK_PREFIX = "lock:seat:";

    // Lua script for atomic check-and-delete
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
        Long.class
    );

    // Lua script for atomic check-and-expire
    private static final DefaultRedisScript<Long> EXTEND_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], ARGV[2]) else return 0 end",
        Long.class
    );

    public RedisLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Acquires distributed locks for multiple seats in sorted order to prevent deadlocks.
     * Returns list of acquired lock keys. If any lock fails, releases all acquired and returns empty list.
     */
    public List<String> acquireLocks(Long showId, List<String> seatNumbers, Duration ttl) {
        List<String> sorted = new ArrayList<>(seatNumbers);
        Collections.sort(sorted);

        String lockValue = UUID.randomUUID().toString();
        List<String> acquired = new ArrayList<>();

        try {
            for (String seat : sorted) {
                String key = LOCK_PREFIX + showId + ":" + seat;
                Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, lockValue, ttl);
                if (Boolean.TRUE.equals(ok)) acquired.add(key);
                else { releaseLocks(acquired, lockValue); return List.of(); }
            }
            return acquired;
        } catch (Exception e) { releaseLocks(acquired, lockValue); throw e; }
    }

    public void releaseLocks(List<String> keys, String lockValue) {
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), lockValue);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Extends the TTL of acquired locks
     */
    public void extendLocks(List<String> keys, String lockValue, Duration ttl) {
        long ttlMs = ttl.toMillis();
        for (String key : keys) {
            try {
                redisTemplate.execute(EXTEND_SCRIPT, List.of(key), lockValue, String.valueOf(ttlMs));
            } catch (Exception ignored) {}
        }
    }
}