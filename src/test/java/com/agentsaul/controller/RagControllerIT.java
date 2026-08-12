package com.agentsaul.controller;

import com.agentsaul.BaseIntegrationTest;
import com.agentsaul.rag.RagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("RAG API Integration Tests")
class RagControllerIT extends BaseIntegrationTest {

    @MockBean
    private RagService ragService;

    @Nested
    @DisplayName("POST /api/rag/chat")
    @WithMockUser(username = "testuser", roles = "USER")
    class Chat {

        @Test
        @DisplayName("should return error event for blank query")
        void shouldRejectBlankQuery() throws Exception {
            mockMvc.perform(post("/api/rag/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\": \"\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("event: error")));
        }
    }

    @Nested
    @DisplayName("GET /api/rag/stats")
    @WithMockUser(username = "testuser", roles = "USER")
    class Stats {

        @Test
        @DisplayName("should return knowledge base stats")
        void shouldReturnStats() throws Exception {
            when(ragService.getStats()).thenReturn(Map.of("documents", 5, "chunks", 42));

            mockMvc.perform(get("/api/rag/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documents").value(5))
                    .andExpect(jsonPath("$.chunks").value(42));
        }
    }

    @Nested
    @DisplayName("GET /api/rag/chunks")
    @WithMockUser(username = "testuser", roles = "USER")
    class Chunks {

        @Test
        @DisplayName("should return chunk preview")
        void shouldReturnChunkPreview() throws Exception {
            when(ragService.previewChunks("token")).thenReturn(List.of());

            mockMvc.perform(get("/api/rag/chunks")
                            .param("strategy", "token"))
                    .andExpect(status().isOk());
        }
    }
}
