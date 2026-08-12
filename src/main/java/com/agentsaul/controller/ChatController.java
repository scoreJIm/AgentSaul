package com.agentsaul.controller;

import com.agentsaul.entity.Conversation;
import com.agentsaul.entity.Message;
import com.agentsaul.service.ChatService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Chat", description = "AI chat endpoints with session management and conversation history")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/session")
    @Timed(value = "chat.session.info", description = "Time taken to retrieve session info")
    @Operation(summary = "Get session info", description = "Returns the current HTTP session ID and associated conversation ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session info retrieved successfully")
    })
    public Map<String, String> sessionInfo(HttpSession session) {
        String uuid = chatService.getOrCreateUuid(session.getId());
        Long convId = chatService.getConversationId(session.getId());
        return Map.of(
                "sessionId", uuid,
                "conversationId", convId != null ? String.valueOf(convId) : "none"
        );
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Timed(value = "chat.stream", description = "Time taken for streaming chat response")
    @Operation(summary = "Send a chat message (streaming)",
            description = "Sends a user message and returns an SSE stream of AI responses. "
                    + "The AI may invoke tools (weather, location, legal, translation, etc.) based on the message content.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream of AI response chunks"),
            @ApiResponse(responseCode = "400", description = "Empty message")
    })
    public Flux<String> chat(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        String message = (String) body.getOrDefault("message", "");
        log.info("[API] POST /chat session={} msgLen={}", chatService.getOrCreateUuid(session.getId()), message.length());
        if (message.isBlank()) {
            return Flux.just("You haven't said anything, counselor.");
        }
        return chatService.chat(session.getId(), message);
    }

    @GetMapping("/conversations")
    @Timed(value = "chat.conversations.list", description = "Time taken to list conversations")
    @Operation(summary = "List all conversations", description = "Returns all saved chat conversations ordered by creation time descending")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of conversations")
    })
    public List<Conversation> listConversations() {
        log.info("[API] GET /conversations");
        return chatService.listConversations();
    }

    @GetMapping("/conversations/{id}/messages")
    @Timed(value = "chat.conversations.messages", description = "Time taken to retrieve conversation messages")
    @Operation(summary = "Get conversation messages", description = "Returns all messages (user, assistant, tool calls) for a given conversation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of messages for the conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public List<Message> getMessages(
            @Parameter(description = "Conversation ID") @PathVariable Long id) {
        log.info("[API] GET /conversations/{}/messages", id);
        return chatService.getMessages(id);
    }

    @GetMapping("/conversations/{id}/tools")
    @Timed(value = "chat.conversations.tools", description = "Time taken to retrieve tool call records")
    @Operation(summary = "Get conversation tool calls", description = "Returns all tool call and tool result messages for a given conversation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of tool messages"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public List<Message> getToolCalls(
            @Parameter(description = "Conversation ID") @PathVariable Long id) {
        log.info("[API] GET /conversations/{}/tools", id);
        return chatService.getToolCalls(id);
    }

    @DeleteMapping("/conversations/{id}")
    @Timed(value = "chat.conversations.delete", description = "Time taken to delete a conversation")
    @Operation(summary = "Delete a conversation", description = "Permanently deletes a conversation and all its messages")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public Map<String, String> deleteConversation(
            @Parameter(description = "Conversation ID") @PathVariable Long id) {
        log.info("[API] DELETE /conversations/{}", id);
        chatService.deleteConversation(id);
        return Map.of("status", "ok");
    }
}
