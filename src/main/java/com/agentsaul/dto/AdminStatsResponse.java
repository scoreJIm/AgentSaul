package com.agentsaul.dto;

import java.util.Map;

public record AdminStatsResponse(
        long totalConversations,
        long totalMessages,
        long totalToolCalls,
        long activeSessions,
        double estimatedCostToday,
        long llmApiErrors,
        Map<String, Long> toolUsageBreakdown
) {}
