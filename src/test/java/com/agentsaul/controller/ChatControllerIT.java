package com.agentsaul.controller;

import com.agentsaul.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@DisplayName("Chat API Integration Tests")
class ChatControllerIT {

    @Autowired private MockMvc mockMvc;
    @MockBean private ChatService chatService;

    @Nested
    @DisplayName("GET /api/session")
    class SessionInfo {

        @Test
        @DisplayName("should return session info")
        void shouldReturnSessionInfo() throws Exception {
            when(chatService.getOrCreateUuid(anyString())).thenReturn("test-uuid-123");
            when(chatService.getConversationId(anyString())).thenReturn(1L);

            mockMvc.perform(get("/api/session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value("test-uuid-123"))
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
    class Chat {

        @Test
        @DisplayName("should return SSE stream for chat message")
        void shouldReturnSseStream() throws Exception {
            when(chatService.getOrCreateUuid(anyString())).thenReturn("test-uuid");
            when(chatService.streamChat(anyString(), anyString(), any()))
                    .thenReturn(Flux.just("Hello", " ", "World"));

            mockMvc.perform(post("/api/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\": \"Hello\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        }
    }
}
