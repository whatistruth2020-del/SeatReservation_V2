package com.paytm.seatreservation.service;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class RedisLockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String lockPrefix = "lock:seat:";
    private final String lockValuePrefix = "lock-value:";

    public RedisLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Acquires distributed locks for multiple seats in sorted order to prevent deadlocks.
     * Returns list of acquired lock keys. If any lock fails, releases all acquired and returns empty list.
     */
    public List<String> acquireLocks(Long showId, List<String> seatNumbers, Duration ttl) {
        // Sort seat numbers to ensure consistent lock acquisition order (deadlock prevention)
        List<String> sortedSeats = new ArrayList<>(seatNumbers);
        Collections.sort(sortedSeats);

        String lockValue = UUID.randomUUID().toString();
        List<String> acquiredLocks = new ArrayList<>();

        try {
            for (String seatNumber : sortedSeats) {
                String lockKey = lockPrefix + showId + ":" + seatNumber;
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, lockValue, ttl);

                if (Boolean.TRUE.equals(acquired)) {
                    acquiredLocks.add(lockKey);
                } else {
                    // Failed to acquire lock, release all acquired
                    releaseLocks(acquiredLocks, lockValue);
                    return List.of();
                }
            }
            return acquiredLocks;
        } catch (Exception e) {
            releaseLocks(acquiredLocks, lockValue);
            throw e;
        }
    }

    public void releaseLocks(List<String> lockKeys, String lockValue) {
        if (lockKeys == null || lockKeys.isEmpty()) {
            return;
        }

        // Use Lua script for atomic check-and-delete
        String luaScript = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
            """;

        for (String lockKey : lockKeys) {
            try {
                redisTemplate.execute(
                        (RedisCallback<Long>) connection -> connection.eval(
                                luaScript.getBytes(),
                                RedisConnection.ReturnType.INTEGER,
                                1,
                                lockKey.getBytes(),
                                lockValue.getBytes()
                        )
                );
            } catch (Exception e) {
                // Log but continue releasing other locks
            }
        }
    }

    /**
     * Extends the TTL of acquired locks
     */
    public void extendLocks(List<String> lockKeys, String lockValue, Duration ttl) {
        String luaScript = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("pexpire", KEYS[1], ARGV[2])
            else
                return 0
            end
            """;

        long ttlMs = ttl.toMillis();
        for (String lockKey : lockKeys) {
            try {
                redisTemplate.execute(
                        (RedisCallback<Long>) connection -> connection.eval(
                                luaScript.getBytes(),
                                RedisConnection.ReturnType.INTEGER,
                                1,
                                lockKey.getBytes(),
                                lockValue.getBytes(),
                                String.valueOf(ttlMs).getBytes()
                        )
                );
            } catch (Exception e) {
                // Log but continue
            }
        }
    }
}