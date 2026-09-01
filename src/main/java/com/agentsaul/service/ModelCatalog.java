package com.agentsaul.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Builds the ordered model failover chain from environment-backed configuration.
 * The primary Spring AI model is always first, followed by distinct fallbacks.
 */
@Component
public class ModelCatalog {

    private final List<String> orderedModels;

    public ModelCatalog(
            @Value("${spring.ai.openai.chat.options.model:qwen3.8-27b}") String primaryModel,
            @Value("${app.models:qwen3.8-max,qwen3.8-flash,kimi-k3}") String fallbackModels) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        addModel(models, primaryModel);
        if (fallbackModels != null) {
            Arrays.stream(fallbackModels.split(","))
                    .map(String::trim)
                    .forEach(model -> addModel(models, model));
        }
        if (models.isEmpty()) {
            models.add("qwen3.8-27b");
        }
        this.orderedModels = List.copyOf(models);
    }

    public List<String> orderedModels() {
        return orderedModels;
    }

    public String primaryModel() {
        return orderedModels.getFirst();
    }

    private static void addModel(LinkedHashSet<String> models, String model) {
        if (model != null && !model.isBlank()) {
            models.add(model.trim());
        }
    }
}
