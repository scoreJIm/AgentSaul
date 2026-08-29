package com.agentsaul.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Real translation tool backed by the configured LLM.
 * <p>
 * Previously returned a canned string; now delegates to a fresh {@link ChatClient}
 * (built without the agent's tools, to avoid recursive tool dispatch).
 */
@Component
public class TranslateTools {

    private static final Logger log = LoggerFactory.getLogger(TranslateTools.class);

    private final ChatClient chatClient;

    public TranslateTools(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Tool(description = "Translate text between languages. Use ISO language codes like zh, en, ja, ko, fr, de, es.")
    public String translate(
            @ToolParam(description = "Text to translate") String text,
            @ToolParam(description = "Target language code, e.g. zh, en, ja") String to,
            @ToolParam(description = "Source language code (optional, auto-detect if not provided)") String from) {
        log.info("[Tool] translate to={} from={} text={}", to, from, text.substring(0, Math.min(50, text.length())));

        String instruction = (from == null || from.isBlank())
                ? "Translate the following text to " + to + ". Return only the translation, no commentary."
                : "Translate the following text from " + from + " to " + to + ". Return only the translation, no commentary.";

        try {
            return chatClient.prompt()
                    .system(instruction)
                    .user(text)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Translation failed: {}", e.getMessage());
            return "Translation failed: " + e.getMessage();
        }
    }
}
