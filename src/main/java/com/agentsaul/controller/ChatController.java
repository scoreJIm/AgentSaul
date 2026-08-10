package com.agentsaul.controller;

import com.agentsaul.entity.Conversation;
import com.agentsaul.entity.Message;
import com.agentsaul.service.ChatService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/session")
    public Map<String, String> sessionInfo(HttpSession session) {
        String uuid = chatService.getOrCreateUuid(session.getId());
        Long convId = chatService.getConversationId(session.getId());
        return Map.of(
                "sessionId", uuid,
                "conversationId", convId != null ? String.valueOf(convId) : "none"
        );
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody Map<String, Object> body, HttpSession session) {
        String message = (String) body.getOrDefault("message", "");
        log.info("[API] POST /chat session={} msgLen={}", chatService.getOrCreateUuid(session.getId()), message.length());
        if (message.isBlank()) {
            return Flux.just("You haven't said anything, counselor.");
        }
        return chatService.chat(session.getId(), message);
    }

    @GetMapping("/conversations")
    public List<Conversation> listConversations() {
        log.info("[API] GET /conversations");
        return chatService.listConversations();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<Message> getMessages(@PathVariable Long id) {
        log.info("[API] GET /conversations/{}/messages", id);
        return chatService.getMessages(id);
    }

    @GetMapping("/conversations/{id}/tools")
    public List<Message> getToolCalls(@PathVariable Long id) {
        log.info("[API] GET /conversations/{}/tools", id);
        return chatService.getToolCalls(id);
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, String> deleteConversation(@PathVariable Long id) {
        log.info("[API] DELETE /conversations/{}", id);
        chatService.deleteConversation(id);
        return Map.of("status", "ok");
    }
}
