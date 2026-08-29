package com.agentsaul.aspect;

import com.agentsaul.annotation.RateLimit;
import com.agentsaul.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final StringRedisTemplate redisTemplate;

    /** Fallback when Redis is unavailable. */
    private final ConcurrentMap<String, WindowEntry> fallbackMap = new ConcurrentHashMap<>();

    public RateLimitAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(com.agentsaul.annotation.RateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);

        String key = buildKey(annotation);
        int limit = annotation.limit();
        int windowSeconds = annotation.windowSeconds();

        try {
            redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            // Redis unavailable — use in-memory fallback
            return checkFallback(joinPoint, key, limit, windowSeconds);
        }

        // Set TTL on first access (key may already exist from prior window)
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl == -1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        String countStr = redisTemplate.opsForValue().get(key);
        long count = countStr != null ? Long.parseLong(countStr) : 0;

        if (count > limit) {
            Long remainingTtl = redisTemplate.getExpire(key);
            long retryAfter = remainingTtl != null && remainingTtl > 0 ? remainingTtl : 60;
            log.warn("Rate limit exceeded: key={} count={} limit={}", key, count, limit);
            throw new RateLimitExceededException(
                    "Too many requests. Please try again in " + retryAfter + " seconds.",
                    retryAfter);
        }

        return joinPoint.proceed();
    }

    private Object checkFallback(ProceedingJoinPoint joinPoint, String key,
                                  int limit, int windowSeconds) throws Throwable {
        long now = System.currentTimeMillis();
        WindowEntry entry = fallbackMap.compute(key, (k, v) -> {
            if (v == null || now - v.windowStart > windowSeconds * 1000L) {
                return new WindowEntry(now, 1);
            }
            v.count.incrementAndGet();
            return v;
        });

        if (entry.count.get() > limit) {
            long elapsed = now - entry.windowStart;
            long retryAfter = Math.max(1, windowSeconds - elapsed / 1000);
            log.warn("Rate limit exceeded (fallback): key={} count={} limit={}", key, entry.count.get(), limit);
            throw new RateLimitExceededException(
                    "Too many requests. Please try again in " + retryAfter + " seconds.",
                    retryAfter);
        }

        return joinPoint.proceed();
    }

    private String buildKey(RateLimit annotation) {
        String scope = annotation.scope().name().toLowerCase();
        String identifier = resolveIdentifier(annotation.scope());
        return "ratelimit:" + scope + ":" + identifier + ":" + getMethodKey();
    }

    private String resolveIdentifier(RateLimit.Scope scope) {
        if (scope == RateLimit.Scope.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        }
        // Fallback to IP
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String getMethodKey() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getMethod() + ":" + request.getRequestURI();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static class WindowEntry {
        final long windowStart;
        final AtomicLong count;

        WindowEntry(long windowStart, long initialCount) {
            this.windowStart = windowStart;
            this.count = new AtomicLong(initialCount);
        }
    }
}
