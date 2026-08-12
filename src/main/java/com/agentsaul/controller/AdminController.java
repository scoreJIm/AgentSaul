package com.agentsaul.controller;

import com.agentsaul.dto.AdminStatsResponse;
import com.agentsaul.repository.ConversationMapper;
import com.agentsaul.repository.MessageMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin dashboard and management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final double AVG_TOKENS_PER_MESSAGE = 500.0;
    private static final double COST_PER_TOKEN = 0.000002;
    private static final LocalDateTime STARTUP_TIME = LocalDateTime.now();

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final StringRedisTemplate redisTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final DataSource dataSource;

    public AdminController(ConversationMapper conversationMapper,
                           MessageMapper messageMapper,
                           StringRedisTemplate redisTemplate,
                           CircuitBreakerRegistry circuitBreakerRegistry,
                           DataSource dataSource) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.redisTemplate = redisTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.dataSource = dataSource;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get admin dashboard statistics",
            description = "Returns aggregate statistics for the last 24 hours including conversation counts, "
                    + "message volumes, tool usage breakdown, active sessions, and estimated costs.")
    public AdminStatsResponse stats() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        log.info("[API] GET /api/admin/stats since={}", since);

        long totalConversations = conversationMapper.countByCreatedAfter(since);
        long totalMessages = messageMapper.countByCreatedAfter(since);
        long totalToolCalls = messageMapper.countToolCallsByCreatedAfter(since);
        long activeSessions = countActiveSessions();
        double estimatedCostToday = totalMessages * AVG_TOKENS_PER_MESSAGE * COST_PER_TOKEN;
        long llmApiErrors = messageMapper.countLlmApiErrorsByCreatedAfter(since);

        List<Map<String, Object>> rawBreakdown = messageMapper.groupToolUsageByCreatedAfter(since);
        Map<String, Long> toolUsageBreakdown = new LinkedHashMap<>();
        for (Map<String, Object> row : rawBreakdown) {
            String toolName = (String) row.get("tool_name");
            Number cnt = (Number) row.get("cnt");
            toolUsageBreakdown.put(toolName, cnt.longValue());
        }

        log.info("[API] stats: conv={} msgs={} tools={} sessions={} cost=${} errors={}",
                totalConversations, totalMessages, totalToolCalls, activeSessions,
                String.format("%.4f", estimatedCostToday), llmApiErrors);

        return new AdminStatsResponse(
                totalConversations,
                totalMessages,
                totalToolCalls,
                activeSessions,
                estimatedCostToday,
                llmApiErrors,
                toolUsageBreakdown
        );
    }

    @GetMapping("/circuit-breakers")
    @Operation(summary = "Get circuit breaker states",
            description = "Returns the current state, failure rate, and slow call rate "
                    + "for all Resilience4j circuit breakers.")
    public Map<String, Object> circuitBreakers() {
        log.info("[API] GET /api/admin/circuit-breakers");

        List<Map<String, Object>> breakerList = new ArrayList<>();
        for (CircuitBreaker cb : circuitBreakerRegistry.getAllCircuitBreakers()) {
            CircuitBreaker.Metrics metrics = cb.getMetrics();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", cb.getName());
            info.put("state", cb.getState().name());
            info.put("failureRate", Math.round(metrics.getFailureRate() * 100.0) / 100.0);
            info.put("slowCallRate", Math.round(metrics.getSlowCallRate() * 100.0) / 100.0);
            breakerList.add(info);
        }

        return Map.of("circuitBreakers", breakerList);
    }

    @GetMapping("/health")
    @Operation(summary = "Get system health summary",
            description = "Returns a health summary including LLM API circuit breaker state, "
                    + "database connectivity, Redis status, and application uptime.")
    public Map<String, Object> health() {
        log.info("[API] GET /api/admin/health");

        Map<String, Object> healthMap = new LinkedHashMap<>();

        // LLM API health (based on circuit breaker state)
        healthMap.put("llmApi", buildLlmApiHealth());

        // Database health
        healthMap.put("database", buildDatabaseHealth());

        // Redis health
        healthMap.put("redis", buildRedisHealth());

        // Uptime
        healthMap.put("uptime", buildUptime());

        return healthMap;
    }

    private Map<String, Object> buildLlmApiHealth() {
        Map<String, Object> llm = new LinkedHashMap<>();
        try {
            var cbOpt = circuitBreakerRegistry.find("llmApi");
            if (cbOpt.isPresent()) {
                CircuitBreaker cb = cbOpt.get();
                String state = cb.getState().name();
                llm.put("status", "OPEN".equals(state) ? "DOWN" : "UP");
                llm.put("circuitBreaker", state);
            } else {
                llm.put("status", "UNKNOWN");
                llm.put("circuitBreaker", "NOT_CONFIGURED");
            }
        } catch (Exception e) {
            llm.put("status", "ERROR");
            llm.put("circuitBreaker", e.getMessage());
        }
        return llm;
    }

    private Map<String, Object> buildDatabaseHealth() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (var conn = dataSource.getConnection()) {
            db.put("status", conn.isValid(2) ? "UP" : "DOWN");
            db.put("activeConnections", 1); // simplified; full pool metrics available via Actuator
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("activeConnections", 0);
            db.put("error", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> buildRedisHealth() {
        Map<String, Object> redis = new LinkedHashMap<>();
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            redis.put("status", "PONG".equals(pong) ? "UP" : "DOWN");
            // Approximate connected clients via keys count
            Set<String> sessionKeys = redisTemplate.keys("agentsaul:session:*");
            redis.put("connectedClients", sessionKeys != null ? sessionKeys.size() : 0);
        } catch (Exception e) {
            redis.put("status", "DOWN");
            redis.put("connectedClients", 0);
            redis.put("error", e.getMessage());
        }
        return redis;
    }

    private String buildUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration d = Duration.ofMillis(uptimeMs);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return String.format("%dh %dm", hours, minutes);
    }

    private long countActiveSessions() {
        try {
            Set<String> keys = redisTemplate.keys("agentsaul:session:*:conversationId");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("Failed to count active sessions from Redis: {}", e.getMessage());
            return 0;
        }
    }
}
