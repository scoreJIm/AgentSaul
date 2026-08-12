package com.agentsaul.controller;

import com.agentsaul.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("MCP Demo API Integration Tests")
class McpDemoControllerIT extends BaseIntegrationTest {

    @MockBean(name = "mcpChatClient")
    private ChatClient mcpChatClient;

    @Nested
    @DisplayName("GET /api/mcp/tools (authenticated)")
    @WithMockUser(username = "testuser", roles = "USER")
    class ListTools {

        @Test
        @DisplayName("should list MCP tools when authenticated")
        void shouldListTools() throws Exception {
            mockMvc.perform(get("/api/mcp/tools"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$[0].name", notNullValue()))
                    .andExpect(jsonPath("$[0].description", notNullValue()));
        }
    }

    @Nested
    @DisplayName("GET /api/mcp/tools (security check)")
    class ListToolsUnauthenticated {

        @Test
        @DisplayName("should reject unauthenticated access to tools")
        void shouldRejectUnauthenticatedAccess() throws Exception {
            mockMvc.perform(get("/api/mcp/tools"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/mcp/chat")
    @WithMockUser(username = "testuser", roles = "USER")
    class Chat {

        @Test
        @DisplayName("should return error event for blank message")
        void shouldRejectBlankMessage() throws Exception {
            mockMvc.perform(post("/api/mcp/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\": \"\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("event: error")));
        }
    }
}
