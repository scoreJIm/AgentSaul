package com.agentsaul.service;

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
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final IntentParser intentParser;

    // per-user sliding window chat memory (keyed by "user:{userId}")
    private final Map<String, ChatMemory> userMemoryMap = new ConcurrentHashMap<>();

    // userId -> current conversationId
    private final Map<String, Long> userConvMap = new ConcurrentHashMap<>();
    // sessionId/identifier -> display UUID
    private final Map<String, String> sessionUuidMap = new ConcurrentHashMap<>();

    @Value("${app.prompt.system}")
    private Resource systemPromptFile;

    @Value("${app.prompt.legal}")
    private Resource legalPromptFile;

    private String systemPrompt;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       ConversationMapper conversationMapper,
                       MessageMapper messageMapper,
                       IntentParser intentParser,
                       LegalTools legalTools,
                       UtilityTools utilityTools,
                       TranslateTools translateTools,
                       WebTools webTools) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.intentParser = intentParser;
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

    public String getOrCreateUuid(String key) {
        return sessionUuidMap.computeIfAbsent(key, k -> UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Chat with user context. userId comes from JWT authentication.
     */
    public Flux<String> chat(String userId, String userMessage) {
        Long userIdLong = parseUserId(userId);
        IntentParser.IntentResult intent = intentParser.parse(userMessage);
        Conversation conv = getOrCreateConversation(userId, userIdLong);

        if (conv.getTitle() == null || conv.getTitle().isBlank()) {
            conv.setTitle(userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);
            conversationMapper.updateTitle(conv);
        }

        // save user message
        Message userMsg = new Message();
        userMsg.setConversationId(conv.getId());
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messageMapper.insert(userMsg);

        String uuid = getOrCreateUuid("user:" + userId);

        log.info("[Business] userId={} uuid={} convId={} intent={} lang={}",
                userId, uuid, conv.getId(), intent.intent(), intent.language());

        String effectivePrompt = systemPrompt;
        if ("zh".equals(intent.language())) {
            effectivePrompt += "\n用户说中文，请用中文回复。";
        }

        // per-user chat memory keyed by userId
        String memoryKey = "user:" + userId;
        ChatMemory memory = userMemoryMap.computeIfAbsent(memoryKey,
                k -> MessageWindowChatMemory.builder().maxMessages(20).build());

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(memory)
                .conversationId(memoryKey)
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
                        Message aiMsg = new Message();
                        aiMsg.setConversationId(conv.getId());
                        aiMsg.setRole("assistant");
                        aiMsg.setContent(response);
                        messageMapper.insert(aiMsg);
                    }
                })
                .onErrorResume(e -> {
                    log.error("[Business] convId={} error: {}", conv.getId(), e.getMessage());
                    return Flux.just("Something went wrong: " + e.getMessage());
                });
    }

    private Conversation getOrCreateConversation(String userId, Long userIdLong) {
        Long convId = userConvMap.get(userId);
        if (convId != null) {
            Conversation conv = conversationMapper.findByIdAndUserId(convId, userIdLong);
            if (conv != null) return conv;
        }
        Conversation conv = new Conversation();
        conv.setUserId(userIdLong);
        conversationMapper.insert(conv);
        userConvMap.put(userId, conv.getId());
        log.info("[Business] new conversation id={} for userId={}", conv.getId(), userId);
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
        userConvMap.entrySet().removeIf(e -> e.getValue().equals(conversationId));
        log.info("[Business] conversation deleted id={}", conversationId);
    }

    public Long getConversationId(String userId) {
        return userConvMap.get(userId);
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
