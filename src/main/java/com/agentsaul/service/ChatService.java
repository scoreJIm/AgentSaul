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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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
                       LegalTools legalTools,
                       UtilityTools utilityTools,
                       TranslateTools translateTools,
                       WebTools webTools) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.intentParser = intentParser;
        this.sessionManager = sessionManager;
        this.chatMemoryFactory = chatMemoryFactory;
        this.chatClient = chatClientBuilder
                .defaultTools(legalTools, utilityTools, translateTools, webTools)
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
     */
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

        // Create ChatMemory via factory: MySQL-backed if DB available, else in-memory fallback.
        // MysqlChatMemory handles message persistence through the MessageChatMemoryAdvisor.
        ChatMemory memory = chatMemoryFactory.create(conv.getId());

        String conversationIdStr = String.valueOf(conv.getId());
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(memory)
                .conversationId(conversationIdStr)
                .build();

        StringBuilder fullResponse = new StringBuilder();

        return chatClient.prompt()
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
                })
                .onErrorResume(e -> {
                    log.error("[Business] convId={} error: {}", conv.getId(), e.getMessage());
                    return Flux.just("Something went wrong: " + e.getMessage());
                });
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
