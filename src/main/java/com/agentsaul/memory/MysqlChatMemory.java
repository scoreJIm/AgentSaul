package com.agentsaul.memory;

import com.agentsaul.entity.Message;
import com.agentsaul.repository.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MySQL-backed ChatMemory implementation.
 * Reads the last 20 messages from the MySQL messages table for a conversation
 * and writes new messages back via MyBatis MessageMapper.
 */
public class MysqlChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(MysqlChatMemory.class);
    private static final int WINDOW_SIZE = 20;

    private final Long conversationId;
    private final MessageMapper messageMapper;

    public MysqlChatMemory(Long conversationId, MessageMapper messageMapper) {
        this.conversationId = conversationId;
        this.messageMapper = messageMapper;
    }

    @Override
    public void add(String conversationIdStr, List<org.springframework.ai.chat.messages.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Long convId = resolveConversationId(conversationIdStr);
        for (org.springframework.ai.chat.messages.Message msg : messages) {
            Message entity = new Message();
            entity.setConversationId(convId);
            entity.setRole(mapRole(msg.getMessageType()));
            entity.setContent(msg.getText() != null ? msg.getText() : "");
            messageMapper.insert(entity);
        }
    }

    @Override
    public List<org.springframework.ai.chat.messages.Message> get(String conversationIdStr) {
        Long convId = resolveConversationId(conversationIdStr);
        List<Message> entities = messageMapper.findByConversationId(convId);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        // Return the last WINDOW_SIZE messages only (matching MessageWindowChatMemory default)
        int fromIndex = Math.max(0, entities.size() - WINDOW_SIZE);
        List<Message> recentEntities = entities.subList(fromIndex, entities.size());

        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();
        for (Message entity : recentEntities) {
            result.add(toSpringMessage(entity));
        }
        return result;
    }

    @Override
    public void clear(String conversationIdStr) {
        Long convId = resolveConversationId(conversationIdStr);
        messageMapper.deleteByConversationId(convId);
        log.info("MysqlChatMemory: cleared messages for conversationId={}", convId);
    }

    private Long resolveConversationId(String conversationIdStr) {
        if (conversationIdStr != null && !conversationIdStr.isBlank()) {
            try {
                return Long.parseLong(conversationIdStr);
            } catch (NumberFormatException e) {
                log.debug("Cannot parse conversationId '{}', using constructor value", conversationIdStr);
            }
        }
        return this.conversationId;
    }

    private String mapRole(MessageType type) {
        return switch (type) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool_result";
        };
    }

    private org.springframework.ai.chat.messages.Message toSpringMessage(Message entity) {
        String role = entity.getRole();
        String content = entity.getContent() != null ? entity.getContent() : "";
        return switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }
}
