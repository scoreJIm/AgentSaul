package com.agentsaul.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Client — ChatClient with direct MCP tools.
 * <p>
 * The MCP demo reuses {@link McpTools} (annotated with {@code @Tool})
 * as direct function-calling tools. This keeps the demo simple and avoids
 * MCP auto-config complexity while still demonstrating the concept.
 */
@Configuration
public class McpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(McpClientConfig.class);

    @Bean
    public ChatClient mcpChatClient(ChatClient.Builder chatClientBuilder, McpTools mcpTools) {
        log.info("MCP Demo ChatClient: using direct tools from McpTools");
        return chatClientBuilder.defaultTools(mcpTools).build();
    }
}
