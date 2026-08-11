package com.agentsaul.controller;

import com.agentsaul.mcp.McpTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(McpDemoController.class)
@Import(McpTools.class)
@DisplayName("MCP Demo API Integration Tests")
class McpDemoControllerIT {

    @Autowired private MockMvc mockMvc;

    @Nested
    @DisplayName("GET /api/mcp/tools")
    class ListTools {

        @Test
        @DisplayName("should list MCP tools")
        void shouldListTools() throws Exception {
            mockMvc.perform(get("/api/mcp/tools"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$[0].name", notNullValue()))
                    .andExpect(jsonPath("$[0].description", notNullValue()));
        }
    }

    @Nested
    @DisplayName("POST /api/mcp/chat")
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
