package com.agentsaul.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TranslateTools {

    private static final Logger log = LoggerFactory.getLogger(TranslateTools.class);

    @Tool(description = "Translate text between languages. Use ISO language codes like zh, en, ja, ko, fr, de, es.")
    public String translate(
            @ToolParam(description = "Text to translate") String text,
            @ToolParam(description = "Target language code, e.g. zh, en, ja") String to,
            @ToolParam(description = "Source language code (optional, auto-detect if not provided)") String from) {
        log.info("[Tool] translate to={} from={} text={}", to, from, text.substring(0, Math.min(50, text.length())));

        Map<String, String> langNames = Map.of(
            "zh", "中文", "en", "English", "ja", "日本語",
            "ko", "한국어", "fr", "Français", "de", "Deutsch", "es", "Español"
        );

        return String.format("Translation request: [%s] → [%s]. Text to translate: \"%s\"",
                from != null ? from : "auto", to, text);
    }
}
