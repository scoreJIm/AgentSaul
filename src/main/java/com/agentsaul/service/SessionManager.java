package com.agentsaul.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed session state manager.
 * Replaces the in-memory ConcurrentHashMaps for session-conversation,
 * session-uuid, and user-memory associations.
 * Falls back to ConcurrentHashMap if Redis is unavailable.
 */
@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private static final String SESSION_CONV_PREFIX = "agentsaul:session:";
    private static final String SESSION_UUID_PREFIX = "agentsaul:session:";
    private static final String USER_MEMORY_PREFIX = "agentsaul:user:";
    private static final long TTL_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;
    private volatile boolean redisAvailable = true;

    // ConcurrentHashMap fallback if Redis unavailable
    private final Map<String, Long> sessionConvFallback = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUuidFallback = new ConcurrentHashMap<>();
    private final Map<String, String> userMemoryFallback = new ConcurrentHashMap<>();

    public SessionManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        try {
            redisTemplate.opsForValue().get("agentsaul:health");
            log.info("SessionManager: Redis connected");
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("SessionManager: Redis unavailable — using ConcurrentHashMap fallback");
        }
    }

    // ── session -> conversationId ──────────────────────────────────────────

    public void setConversationId(String sessionId, Long conversationId) {
        if (redisAvailable) {
            try {
                String key = SESSION_CONV_PREFIX + sessionId + ":conversationId";
                redisTemplate.opsForValue().set(key, String.valueOf(conversationId),
                        TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Redis setConversationId failed, using fallback: {}", e.getMessage());
                sessionConvFallback.put(sessionId, conversationId);
            }
        } else {
            sessionConvFallback.put(sessionId, conversationId);
        }
    }

    public Long getConversationId(String sessionId) {
        if (redisAvailable) {
            try {
                String key = SESSION_CONV_PREFIX + sessionId + ":conversationId";
                String val = redisTemplate.opsForValue().get(key);
                if (val != null && !val.isEmpty()) {
                    return Long.parseLong(val);
                }
                return null;
            } catch (Exception e) {
                log.warn("Redis getConversationId failed, using fallback: {}", e.getMessage());
                return sessionConvFallback.get(sessionId);
            }
        }
        return sessionConvFallback.get(sessionId);
    }

    // ── session -> uuid ────────────────────────────────────────────────────

    public void setUuid(String sessionId, String uuid) {
        if (redisAvailable) {
            try {
                String key = SESSION_UUID_PREFIX + sessionId + ":uuid";
                redisTemplate.opsForValue().set(key, uuid, TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Redis setUuid failed, using fallback: {}", e.getMessage());
                sessionUuidFallback.put(sessionId, uuid);
            }
        } else {
            sessionUuidFallback.put(sessionId, uuid);
        }
    }

    public String getOrCreateUuid(String sessionId) {
        if (redisAvailable) {
            try {
                String key = SESSION_UUID_PREFIX + sessionId + ":uuid";
                String val = redisTemplate.opsForValue().get(key);
                if (val != null && !val.isEmpty()) {
                    return val;
                }
                String uuid = UUID.randomUUID().toString().substring(0, 8);
                redisTemplate.opsForValue().set(key, uuid, TTL_MINUTES, TimeUnit.MINUTES);
                return uuid;
            } catch (Exception e) {
                log.warn("Redis getOrCreateUuid failed, using fallback: {}", e.getMessage());
                return sessionUuidFallback.computeIfAbsent(sessionId,
                        k -> UUID.randomUUID().toString().substring(0, 8));
            }
        }
        return sessionUuidFallback.computeIfAbsent(sessionId,
                k -> UUID.randomUUID().toString().substring(0, 8));
    }

    // ── user -> memory (current conversationId reference) ──────────────────

    public void setUserMemory(String userId, String conversationId) {
        if (redisAvailable) {
            try {
                String key = USER_MEMORY_PREFIX + userId + ":memory";
                redisTemplate.opsForValue().set(key, conversationId, TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Redis setUserMemory failed, using fallback: {}", e.getMessage());
                userMemoryFallback.put(userId, conversationId);
            }
        } else {
            userMemoryFallback.put(userId, conversationId);
        }
    }

    public String getUserMemory(String userId) {
        if (redisAvailable) {
            try {
                String key = USER_MEMORY_PREFIX + userId + ":memory";
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("Redis getUserMemory failed, using fallback: {}", e.getMessage());
                return userMemoryFallback.get(userId);
            }
        }
        return userMemoryFallback.get(userId);
    }

    // ── cleanup ────────────────────────────────────────────────────────────

    public void removeSession(String sessionId) {
        if (redisAvailable) {
            try {
                redisTemplate.delete(SESSION_CONV_PREFIX + sessionId + ":conversationId");
                redisTemplate.delete(SESSION_UUID_PREFIX + sessionId + ":uuid");
            } catch (Exception e) {
                log.warn("Redis removeSession failed: {}", e.getMessage());
            }
        }
        sessionConvFallback.remove(sessionId);
        sessionUuidFallback.remove(sessionId);
    }

    public void removeUserMemory(String userId) {
        if (redisAvailable) {
            try {
                redisTemplate.delete(USER_MEMORY_PREFIX + userId + ":memory");
            } catch (Exception e) {
                log.warn("Redis removeUserMemory failed: {}", e.getMessage());
            }
        }
        userMemoryFallback.remove(userId);
    }
}
