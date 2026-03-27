package com.threatdetection.intelligence.feed;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FeedRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(FeedRateLimiter.class);

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(24))
            .maximumSize(50)
            .build();

    public boolean tryAcquire(String feedName, int maxRequestsPerHour) {
        TokenBucket bucket = buckets.get(feedName, k -> new TokenBucket(maxRequestsPerHour));
        boolean allowed = bucket.tryConsume();
        if (!allowed) {
            log.warn("Rate limited: feed={} (max {} req/hr)", feedName, maxRequestsPerHour);
        }
        return allowed;
    }

    private static class TokenBucket {
        private final int maxTokens;
        private final AtomicLong tokens;
        private volatile long lastRefillTime;
        private final long refillIntervalMs;

        TokenBucket(int maxRequestsPerHour) {
            this.maxTokens = maxRequestsPerHour;
            this.tokens = new AtomicLong(maxRequestsPerHour);
            this.lastRefillTime = System.currentTimeMillis();
            this.refillIntervalMs = 3_600_000L / Math.max(1, maxRequestsPerHour);
        }

        boolean tryConsume() {
            refill();
            long current = tokens.get();
            if (current > 0 && tokens.compareAndSet(current, current - 1)) {
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed < refillIntervalMs) {
                return;
            }

            long tokensToAdd = elapsed / refillIntervalMs;
            if (tokensToAdd > 0) {
                long current = tokens.get();
                long newTokens = Math.min(maxTokens, current + tokensToAdd);
                tokens.set(newTokens);
                lastRefillTime = now;
            }
        }
    }
}
