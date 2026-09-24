package com.clothing.app.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final long lockoutDurationSeconds;
    private final ConcurrentMap<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(5, 15);
    }

    public LoginAttemptService(
            @Value("${app.security.max-login-attempts:5}") int maxAttempts,
            @Value("${app.security.lockout-duration-minutes:15}") int lockoutDurationMinutes) {
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.lockoutDurationSeconds = Math.max(lockoutDurationMinutes, 1) * 60L;
    }

    public void loginSucceeded(String key) {
        if (key != null) {
            attemptsCache.remove(normalizeKey(key));
        }
    }

    public void loginFailed(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String normalized = normalizeKey(key);
        Instant now = Instant.now();
        attemptsCache.compute(normalized, (k, existing) -> {
            if (existing == null || isExpired(existing, now)) {
                return new AttemptRecord(1, now);
            }
            return new AttemptRecord(existing.attempts() + 1, now);
        });
    }

    public boolean isBlocked(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = normalizeKey(key);
        AttemptRecord record = attemptsCache.get(normalized);
        if (record == null) {
            return false;
        }
        if (isExpired(record, Instant.now())) {
            attemptsCache.remove(normalized, record);
            return false;
        }
        return record.attempts() >= maxAttempts;
    }

    public long getRemainingLockoutSeconds(String key) {
        if (key == null || key.isBlank()) {
            return 0L;
        }
        String normalized = normalizeKey(key);
        AttemptRecord record = attemptsCache.get(normalized);
        if (record == null || record.attempts() < maxAttempts) {
            return 0L;
        }
        long elapsed = Instant.now().getEpochSecond() - record.lastAttempt().getEpochSecond();
        long remaining = lockoutDurationSeconds - elapsed;
        return Math.max(remaining, 0L);
    }

    public void reset() {
        attemptsCache.clear();
    }

    private boolean isExpired(AttemptRecord record, Instant now) {
        return now.getEpochSecond() - record.lastAttempt().getEpochSecond() > lockoutDurationSeconds;
    }

    private String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private record AttemptRecord(int attempts, Instant lastAttempt) {
    }
}
