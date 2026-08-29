package com.agentsaul.config;

import com.agentsaul.memory.PostgresChatMemory;
import com.agentsaul.repository.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

/**
 * Factory that creates ChatMemory instances.
 * Returns PostgresChatMemory backed by PostgreSQL if the database is available;
 * falls back to in-memory MessageWindowChatMemory otherwise.
 */
public class ChatMemoryFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryFactory.class);

    private final MessageMapper messageMapper;
    private volatile boolean dbAvailable = true;

    public ChatMemoryFactory(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public ChatMemory create(Long conversationId) {
        if (!dbAvailable) {
            return MessageWindowChatMemory.builder().maxMessages(20).build();
        }
        try {
            // Lightweight DB availability check
            messageMapper.findByConversationId(conversationId != null ? conversationId : 0L);
            return new PostgresChatMemory(conversationId != null ? conversationId : 0L, messageMapper);
        } catch (Exception e) {
            dbAvailable = false;
            log.warn("PostgreSQL unavailable for ChatMemory, using in-memory fallback: {}", e.getMessage());
            return MessageWindowChatMemory.builder().maxMessages(20).build();
        }
    }
}
