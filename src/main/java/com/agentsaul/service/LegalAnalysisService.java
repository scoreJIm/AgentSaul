package com.agentsaul.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Demonstrates Spring AI <b>structured output</b>: the model is asked for JSON, and
 * {@code ChatClient.call().entity()} deserializes it straight into a Java record —
 * no hand-parsed JSON, no string scraping.
 */
@Service
public class LegalAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LegalAnalysisService.class);

    private final ChatClient chatClient;

    public LegalAnalysisService(ChatClient.Builder builder) {
        // A dedicated client (no agent tools) so a structured-output call can never
        // itself trigger tool dispatch.
        this.chatClient = builder.build();
    }

    /** Structured target: the LLM's JSON is mapped into this record. */
    public record CaseAnalysis(
            String caseType,
            String urgency,
            String summary,
            List<String> nextSteps) {}

    public CaseAnalysis analyze(String userMessage) {
        log.info("[Analysis] analyze messageLen={}", userMessage == null ? 0 : userMessage.length());
        return chatClient.prompt()
                .system("""
                        你是一名法律咨询助手。分析用户的问题，并严格返回 JSON，不要返回任何其他文字。
                        JSON 结构（字段顺序不限）：
                        {
                          "caseType": "案件类型（劳动纠纷 / 合同 / 婚姻家庭 / 刑事 / 其他）",
                          "urgency": "紧急程度（high / medium / low）",
                          "summary": "一句话摘要",
                          "nextSteps": ["建议的下一步行动"]
                        }""")
                .user(userMessage)
                .call()
                .entity(CaseAnalysis.class);
    }
}
