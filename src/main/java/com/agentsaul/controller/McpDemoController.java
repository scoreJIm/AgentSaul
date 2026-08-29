package com.agentsaul.controller;

import com.agentsaul.annotation.RateLimit;
import com.agentsaul.mcp.McpTools;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP Demo", description = "Model Context Protocol tool discovery and chat demonstration endpoints")
public class McpDemoController {

    private static final Logger log = LoggerFactory.getLogger(McpDemoController.class);
    private final ChatClient mcpChatClient;
    private final McpTools mcpTools;

    public McpDemoController(ChatClient mcpChatClient, McpTools mcpTools) {
        this.mcpChatClient = mcpChatClient;
        this.mcpTools = mcpTools;
    }

    @GetMapping("/tools")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "mcp.tools.list", description = "Time taken to list MCP tools")
    @Operation(summary = "List MCP tools", description = "Discovers and returns all MCP tools exposed by this server, "
            + "including their names, descriptions, parameters, and JSON schemas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of available MCP tools with schemas")
    })
    public List<Map<String, Object>> listTools() {
        log.info("[MCP Demo] GET /tools");
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Method m : mcpTools.getClass().getDeclaredMethods()) {
            var toolAnn = m.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
            if (toolAnn == null) continue;
            List<Map<String, String>> params = new ArrayList<>();
            for (var p : m.getParameters()) {
                var paramAnn = p.getAnnotation(org.springframework.ai.tool.annotation.ToolParam.class);
                if (paramAnn != null) {
                    params.add(Map.of("name", p.getName(), "description", paramAnn.description()));
                }
            }
            tools.add(Map.of("name", toolAnn.name(), "description", toolAnn.description(),
                    "parameters", (Object) params));
        }
        return tools;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @RateLimit(limit = 10, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "mcp.chat.stream", description = "Time taken for MCP streaming chat")
    @Operation(summary = "Chat via MCP tools (streaming)",
            description = "Sends a message through the LLM with MCP tool access. "
                    + "The LLM can invoke MCP tools for legal statute lookup, case analysis, and server time. "
                    + "Response is streamed as SSE with typed events: answer, done, error.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream with MCP-assisted response"),
            @ApiResponse(responseCode = "400", description = "Empty message")
    })
    public Flux<String> chat(@RequestBody Map<String, Object> body) {
        String message = (String) body.getOrDefault("message", "");
        log.info("[MCP Demo] chat msgLen={}", message.length());
        if (message.isBlank()) {
            return Flux.just("event: error\ndata: 请输入消息\n\n");
        }

        StringBuilder full = new StringBuilder();
        return Flux.concat(
                Flux.just("event: answer\ndata: \n\n"),
                mcpChatClient.prompt()
                        .system("""
                                你是一个 MCP (Model Context Protocol) 演示助手。
                                你有工具可用，当用户问题适合使用工具时请主动调用。
                                用中文回复，说明你使用了哪个工具以及原因。""")
                        .user(message)
                        .stream()
                        .content()
                        .map(token -> "data: " + escapeJson(token) + "\n\n")
                        .doOnNext(full::append),
                Flux.just("event: done\ndata: {}\n\n")
        ).onErrorResume(e -> {
            log.error("[MCP Demo] error: {}", e.getMessage());
            return Flux.just("event: error\ndata: " + escapeJson(e.getMessage()) + "\n\n");
        });
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
