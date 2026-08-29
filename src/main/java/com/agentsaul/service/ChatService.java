package com.agentsaul.service;

import com.agentsaul.config.ChatMemoryFactory;
import com.agentsaul.entity.Conversation;
import com.agentsaul.entity.Message;
import com.agentsaul.repository.ConversationMapper;
import com.agentsaul.repository.MessageMapper;
import com.agentsaul.tool.LegalTools;
import com.agentsaul.tool.TranslateTools;
import com.agentsaul.tool.UtilityTools;
import com.agentsaul.tool.WebTools;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final IntentParser intentParser;
    private final SessionManager sessionManager;
    private final ChatMemoryFactory chatMemoryFactory;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final MeterRegistry meterRegistry;

    @Value("${app.prompt.system}")
    private Resource systemPromptFile;

    @Value("${app.prompt.legal}")
    private Resource legalPromptFile;

    private String systemPrompt;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       ConversationMapper conversationMapper,
                       MessageMapper messageMapper,
                       IntentParser intentParser,
                       SessionManager sessionManager,
                       ChatMemoryFactory chatMemoryFactory,
                       TimeLimiterRegistry timeLimiterRegistry,
                       MeterRegistry meterRegistry,
                       LegalTools legalTools,
                       UtilityTools utilityTools,
                       TranslateTools translateTools,
                       WebTools webTools,
                       ToolCallback dayOfWeekCallback) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.intentParser = intentParser;
        this.sessionManager = sessionManager;
        this.chatMemoryFactory = chatMemoryFactory;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.meterRegistry = meterRegistry;
        this.chatClient = chatClientBuilder
                .defaultTools(legalTools, utilityTools, translateTools, webTools)
                .defaultToolCallbacks(dayOfWeekCallback)
                .build();
    }

    @PostConstruct
    void loadPrompts() {
        try {
            String main = systemPromptFile.getContentAsString(StandardCharsets.UTF_8);
            String legal = legalPromptFile.getContentAsString(StandardCharsets.UTF_8);
            this.systemPrompt = main + "\n\n" + legal;
            log.info("Prompts loaded: system={} chars, legal={} chars", main.length(), legal.length());
        } catch (Exception e) {
            log.error("Failed to load prompts: {}", e.getMessage());
            this.systemPrompt = "You are Saul Goodman, a sharp and practical lawyer.";
        }
    }

    /**
     * Chat with session and user context.
     * sessionId comes from Spring Session (Redis-backed).
     * userId comes from JWT authentication.
     *
     * Protected by Resilience4j circuit breaker ("llmApi"): 3 failures → open for 30s.
     * Protected by TimeLimiter ("llmApi"): 60s timeout applied to the LLM stream.
     */
    @CircuitBreaker(name = "llmApi", fallbackMethod = "chatFallback")
    public Flux<String> chat(String sessionId, String userId, String userMessage) {
        Long userIdLong = parseUserId(userId);
        IntentParser.IntentResult intent = intentParser.parse(userMessage);
        Conversation conv = getOrCreateConversation(sessionId, userId, userIdLong);

        if (conv.getTitle() == null || conv.getTitle().isBlank()) {
            conv.setTitle(userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);
            conversationMapper.updateTitle(conv);
        }

        String uuid = sessionManager.getOrCreateUuid(sessionId);

        log.info("[Business] sessionId={} userId={} uuid={} convId={} intent={} lang={}",
                sessionId, userId, uuid, conv.getId(), intent.intent(), intent.language());

        String effectivePrompt = systemPrompt;
        if ("zh".equals(intent.language())) {
            effectivePrompt += "\n用户说中文，请用中文回复。";
        }

        // Create ChatMemory via factory: PostgreSQL-backed if DB available, else in-memory fallback.
        // PostgresChatMemory handles message persistence through the MessageChatMemoryAdvisor.
        ChatMemory memory = chatMemoryFactory.create(conv.getId());

        String conversationIdStr = String.valueOf(conv.getId());
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(memory)
                .conversationId(conversationIdStr)
                .build();

        StringBuilder fullResponse = new StringBuilder();

        Flux<String> llmStream = chatClient.prompt()
                .advisors(memoryAdvisor)
                .system(effectivePrompt)
                .user(userMessage)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    String response = fullResponse.toString();
                    if (!response.isBlank()) {
                        log.debug("[Business] convId={} responseLen={}", conv.getId(), response.length());
                    }
                });

        // Apply TimeLimiter (60s) to the reactive LLM stream.
        // When the circuit breaker is CLOSED, the call proceeds normally and the
        // TimeLimiterOperator enforces the timeout.  If the LLM stream emits no
        // items within the configured duration a TimeoutException is signaled,
        // which the CircuitBreaker aspect records as a failure.
        return Flux.from(
                TimeLimiterOperator.<String>of(timeLimiterRegistry.timeLimiter("llmApi"))
                        .apply(llmStream)
        );
    }

    /**
     * Circuit breaker fallback for LLM API calls.
     * Invoked when the circuit is OPEN or when a call fails while CLOSED/HALF_OPEN.
     */
    @SuppressWarnings("unused")
    public Flux<String> chatFallback(String sessionId, String userId, String userMessage, Throwable t) {
        log.warn("[Business] LLM API circuit breaker fallback triggered: sessionId={} userId={} error={}",
                sessionId, userId, t.getMessage());
        meterRegistry.counter("agentsaul.circuitbreaker.llmapi.fallback").increment();
        return Flux.just("I'm sorry, I'm having trouble connecting to my brain right now. "
                + "Please try again in a moment.");
    }

    private Conversation getOrCreateConversation(String sessionId, String userId, Long userIdLong) {
        // First, try to get the conversation from the current session (Redis-backed)
        Long convId = sessionManager.getConversationId(sessionId);
        if (convId != null) {
            Conversation conv = conversationMapper.findByIdAndUserId(convId, userIdLong);
            if (conv != null) {
                log.debug("[Business] reconnected sessionId={} to convId={}", sessionId, convId);
                return conv;
            }
            // Conversation deleted or ownership changed, clear stale reference
            sessionManager.removeSession(sessionId);
        }

        // No active conversation — create a new one
        Conversation conv = new Conversation();
        conv.setUserId(userIdLong);
        conversationMapper.insert(conv);

        // Persist session -> conversationId in Redis (with ConcurrentHashMap fallback)
        sessionManager.setConversationId(sessionId, conv.getId());
        sessionManager.setUserMemory(userId, String.valueOf(conv.getId()));

        log.info("[Business] new conversation id={} for sessionId={} userId={}", conv.getId(), sessionId, userId);
        return conv;
    }

    public List<Conversation> listConversations(Long userIdLong) {
        if (userIdLong != null) {
            return conversationMapper.findByUserId(userIdLong);
        }
        return conversationMapper.findAll();
    }

    @Cacheable(value = "convMessages", key = "#conversationId", unless = "#result == null || #result.isEmpty()")
    public List<Message> getMessages(Long conversationId) {
        return messageMapper.findByConversationId(conversationId);
    }

    @Cacheable(value = "toolCalls", key = "#conversationId", unless = "#result == null || #result.isEmpty()")
    public List<Message> getToolCalls(Long conversationId) {
        return messageMapper.findToolCallsByConversationId(conversationId);
    }

    @CacheEvict(value = {"convMessages", "toolCalls"}, key = "#conversationId")
    public void deleteConversation(Long conversationId, Long userIdLong) {
        if (userIdLong != null) {
            conversationMapper.deleteByIdAndUserId(conversationId, userIdLong);
        } else {
            messageMapper.deleteByConversationId(conversationId);
            conversationMapper.deleteById(conversationId);
        }
        log.info("[Business] conversation deleted id={}", conversationId);
    }

    public String exportConversation(Long conversationId, Long userIdLong) {
        Conversation conv = conversationMapper.findByIdAndUserId(conversationId, userIdLong);
        if (conv == null) {
            return null;
        }
        List<Message> messages = messageMapper.findByConversationId(conversationId);

        StringBuilder sb = new StringBuilder();
        sb.append("# Conversation: ").append(conv.getTitle() != null ? conv.getTitle() : "Untitled").append("\n");
        sb.append("Date: ").append(conv.getCreatedAt()).append("\n");
        sb.append("Messages: ").append(messages.size()).append("\n\n");
        sb.append("---\n\n");

        for (Message msg : messages) {
            String roleLabel = switch (msg.getRole()) {
                case "user" -> "**User**";
                case "assistant" -> "**AgentSaul**";
                case "tool_call" ->
                        "[Tool: " + (msg.getToolName() != null ? msg.getToolName() : "unknown") + "]";
                case "tool_result" ->
                        "[Tool Result: " + (msg.getToolName() != null ? msg.getToolName() : "unknown") + "]";
                default -> "**" + msg.getRole() + "**";
            };

            sb.append(roleLabel);
            if (msg.getCreatedAt() != null) {
                sb.append(" (").append(msg.getCreatedAt()).append(")");
            }
            sb.append(":\n");
            if (msg.getContent() != null) {
                sb.append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n");
        return sb.toString();
    }

    public Long getConversationId(String sessionId) {
        return sessionManager.getConversationId(sessionId);
    }

    public String getOrCreateUuid(String sessionId) {
        return sessionManager.getOrCreateUuid(sessionId);
    }

    private Long parseUserId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            // for non-numeric user IDs (e.g., "apikey-user"), use a hash
            return (long) Math.abs(userId.hashCode());
        }
    }
}
