package com.agentsaul.controller;

import com.agentsaul.dto.AdminStatsResponse;
import com.agentsaul.repository.ConversationMapper;
import com.agentsaul.repository.MessageMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final StringRedisTemplate redisTemplate;

    public AdminController(ConversationMapper conversationMapper,
                           MessageMapper messageMapper,
                           StringRedisTemplate redisTemplate) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.redisTemplate = redisTemplate;
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
