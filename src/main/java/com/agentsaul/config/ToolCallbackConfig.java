package com.agentsaul.config;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

/**
 * Demonstrates <b>explicit ToolCallback registration</b> — the underlying mechanism
 * behind {@code @Tool}. Instead of annotating a bean, a plain {@code Function} is
 * wrapped into a {@link ToolCallback} and exposed as a Spring bean.
 */
@Configuration(proxyBeanMethods = false)
public class ToolCallbackConfig {

    /** Tool input schema (used to generate the tool's JSON arguments). */
    public record DateInput(String date) {}

    @Bean
    public ToolCallback dayOfWeekCallback() {
        return FunctionToolCallback.builder("dayOfWeek", (DateInput in) -> {
                    LocalDate d = LocalDate.parse(in.date());
                    return d.getDayOfWeek() + " (" + d + ")";
                })
                .description("Get the day of the week for a date in yyyy-MM-dd format")
                .inputType(DateInput.class)
                .build();
    }
}
