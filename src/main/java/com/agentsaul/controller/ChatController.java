package com.agentsaul.controller;

import com.agentsaul.annotation.RateLimit;
import com.agentsaul.dto.ChatRequest;
import com.agentsaul.entity.Conversation;
import com.agentsaul.entity.Message;
import com.agentsaul.service.ChatService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Timed(value = "chat.session.info", description = "Time taken to retrieve session info")
    @Operation(summary = "Get session info",
            description = "Returns the current Spring Session ID, user ID, and associated conversation ID. "
                    + "On reconnect, the session is loaded from Redis and the conversation context is restored from PostgreSQL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session info retrieved successfully")
    })
    public Map<String, String> sessionInfo(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String sessionId = session.getId();
        String userId = getCurrentUserId();
        Long convId = chatService.getConversationId(sessionId);
        String uuid = chatService.getOrCreateUuid(sessionId);

        log.info("[API] GET /session sessionId={} userId={} convId={}", sessionId, userId,
                convId != null ? convId : "none");

        return Map.of(
                "sessionId", sessionId,
                "userId", userId,
                "uuid", uuid,
                "conversationId", convId != null ? String.valueOf(convId) : "none"
        );
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @RateLimit(limit = 20, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "chat.stream", description = "Time taken for streaming chat response")
    @Operation(summary = "Send a chat message (streaming)",
            description = "Sends a user message and returns an SSE stream of AI responses. "
                    + "The AI may invoke tools (weather, location, legal, translation, etc.) based on the message content. "
                    + "Uses Redis-backed Spring Session to persist conversation context across pod restarts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream of AI response chunks"),
            @ApiResponse(responseCode = "400", description = "Empty message or validation error")
    })
    public Flux<String> chat(@Valid @RequestBody ChatRequest request,
                              HttpServletRequest httpRequest) {
        String userId = getCurrentUserId();
        String message = request.getMessage();

        // Use Spring Session's session ID (Redis-backed)
        HttpSession session = httpRequest.getSession();
        String sessionId = session.getId();

        log.info("[API] POST /chat sessionId={} userId={} msgLen={}", sessionId, userId, message.length());

        if (message.isBlank()) {
            return Flux.just("You haven't said anything, counselor.");
        }
        return chatService.chat(sessionId, userId, message);
    }

    @GetMapping("/conversations")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "chat.conversations.list", description = "Time taken to list conversations")
    @Operation(summary = "List all conversations",
            description = "Returns all saved chat conversations for the current user ordered by creation time descending")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of conversations")
    })
    public List<Conversation> listConversations() {
        String userId = getCurrentUserId();
        log.info("[API] GET /conversations userId={}", userId);
        Long userIdLong = parseUserId(userId);
        return chatService.listConversations(userIdLong);
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "chat.conversations.messages", description = "Time taken to retrieve conversation messages")
    @Operation(summary = "Get conversation messages",
            description = "Returns all messages (user, assistant, tool calls) for a given conversation")
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "chat.conversations.tools", description = "Time taken to retrieve tool call records")
    @Operation(summary = "Get conversation tool calls",
            description = "Returns all tool call and tool result messages for a given conversation")
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "chat.conversations.delete", description = "Time taken to delete a conversation")
    @Operation(summary = "Delete a conversation",
            description = "Permanently deletes a conversation and all its messages")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public Map<String, String> deleteConversation(
            @Parameter(description = "Conversation ID") @PathVariable Long id) {
        String userId = getCurrentUserId();
        log.info("[API] DELETE /conversations/{} userId={}", id, userId);
        Long userIdLong = parseUserId(userId);
        chatService.deleteConversation(id, userIdLong);
        return Map.of("status", "ok");
    }

    @GetMapping(value = "/conversations/{id}/export", produces = "text/markdown")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @RateLimit(limit = 10, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "chat.conversations.export", description = "Time taken to export a conversation")
    @Operation(summary = "Export conversation as markdown",
            description = "Exports a conversation and all its messages as a downloadable markdown file. "
                    + "Users can only export their own conversations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Markdown export of the conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public ResponseEntity<String> exportConversation(
            @Parameter(description = "Conversation ID") @PathVariable Long id) {
        String userId = getCurrentUserId();
        Long userIdLong = parseUserId(userId);
        log.info("[API] GET /conversations/{}/export userId={}", id, userId);

        String markdown = chatService.exportConversation(id, userIdLong);
        if (markdown == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/markdown"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"conversation-" + id + ".md\"")
                .body(markdown);
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }

    private Long parseUserId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return (long) Math.abs(userId.hashCode());
        }
    }
}
