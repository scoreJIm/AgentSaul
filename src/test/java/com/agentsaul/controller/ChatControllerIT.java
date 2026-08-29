package com.agentsaul.controller;

import com.agentsaul.BaseIntegrationTest;
import com.agentsaul.entity.Conversation;
import com.agentsaul.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Chat API Integration Tests")
class ChatControllerIT extends BaseIntegrationTest {

    @MockBean
    private ChatService chatService;

    @Nested
    @DisplayName("GET /api/session")
    @WithMockUser(username = "testuser", roles = "USER")
    class SessionInfo {

        @Test
        @DisplayName("should return session info")
        void shouldReturnSessionInfo() throws Exception {
            when(chatService.getOrCreateUuid(anyString())).thenReturn("test-uuid-123");
            when(chatService.getConversationId(anyString())).thenReturn(1L);

            mockMvc.perform(get("/api/session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("testuser"))
                    .andExpect(jsonPath("$.uuid").value("test-uuid-123"))
                    .andExpect(jsonPath("$.conversationId").value("1"));
        }

        @Test
        @DisplayName("should return 'none' when no conversation")
        void shouldReturnNoneWhenNoConversation() throws Exception {
            when(chatService.getOrCreateUuid(anyString())).thenReturn("test-uuid-456");
            when(chatService.getConversationId(anyString())).thenReturn(null);

            mockMvc.perform(get("/api/session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conversationId").value("none"));
        }
    }

    @Nested
    @DisplayName("POST /api/chat")
    @WithMockUser(username = "testuser", roles = "USER")
    class Chat {

        @Test
        @DisplayName("should return SSE stream for chat message")
        void shouldReturnSseStream() throws Exception {
            when(chatService.getOrCreateUuid(anyString())).thenReturn("test-uuid");
            when(chatService.chat(anyString(), anyString(), anyString()))
                    .thenReturn(Flux.just("Hello", " ", "World"));

            mockMvc.perform(post("/api/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\": \"Hello\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        }
    }

    @Nested
    @DisplayName("Security: POST /api/chat")
    class ChatSecurity {

        @Test
        @DisplayName("should reject unauthenticated SSE streaming request")
        void shouldRejectUnauthenticatedSseRequest() throws Exception {
            mockMvc.perform(post("/api/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\": \"Hello\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/conversations")
    @WithMockUser(username = "testuser", roles = "USER")
    class ConversationList {

        @Test
        @DisplayName("should return empty conversation list")
        void shouldReturnEmptyConversationList() throws Exception {
            when(chatService.listConversations(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/conversations"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("should return conversations for authenticated user")
        void shouldReturnConversations() throws Exception {
            Conversation conv = new Conversation();
            conv.setId(1L);
            conv.setTitle("Hello World");
            when(chatService.listConversations(any())).thenReturn(List.of(conv));

            mockMvc.perform(get("/api/conversations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].title").value("Hello World"));
        }
    }
}
