package com.example.demo2.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class TokenBucketRateLimiterService {

    private static final String LUA_SCRIPT_PATH = "scripts/token-bucket.lua";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> tokenBucketScript;

    public TokenBucketRateLimiterService(
            ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = RedisScript.of(
                new ClassPathResource(LUA_SCRIPT_PATH), Long.class);
    }

    /**
     * Attempt to consume a token from the bucket identified by {@code key}.
     *
     * @param key          the bucket key (e.g. user ID, IP address, API key)
     * @param capacity     maximum number of tokens the bucket can hold
     * @param refillRate   number of tokens added per second
     * @return {@code true} if a token was consumed (request allowed),
     *         {@code false} if no tokens available (request denied)
     */
    @SuppressWarnings("unchecked")
    public Mono<Boolean> tryConsume(String key, long capacity, double refillRate) {
        long now = Instant.now().toEpochMilli();

        return redisTemplate.execute(
                        tokenBucketScript,
                        List.of(key),
                        List.of(capacity, refillRate, now)
                )
                .next()
                .map(result -> ((Number) result).longValue() == 1L);
    }

    /**
     * Returns the current token count for the given bucket without consuming.
     */
    public Mono<Double> getTokenCount(String key) {
        return redisTemplate.opsForHash()
                .get(key, "tokens")
                .map(v -> ((Number) v).doubleValue())
                .defaultIfEmpty(0.0);
    }

    /**
     * Returns the time-to-live of the bucket key in seconds.
     */
    public Mono<Long> getTTL(String key) {
        return redisTemplate.getExpire(key)
                .map(Duration::getSeconds);
    }

    /**
     * Manually sets the token count for a bucket (useful for testing or reset).
     */
    public Mono<Boolean> resetBucket(String key, long capacity, long ttlSeconds) {
        ReactiveHashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();

        return hashOps.putAll(key, Map.of(
                        "tokens", (double) capacity,
                        "last_refill_time", (double) Instant.now().toEpochMilli()
                ))
                .flatMap(r -> redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds)))
                .thenReturn(true);
    }
}