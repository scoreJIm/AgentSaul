package com.agentsaul.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCatalogTest {

    @Test
    void placesPrimaryFirstAndRemovesDuplicateFallbacks() {
        ModelCatalog catalog = new ModelCatalog(
                "qwen3.8-27b",
                "qwen3.8-max, qwen3.8-27b, qwen3.8-flash, qwen3.8-max");

        assertThat(catalog.orderedModels()).containsExactly(
                "qwen3.8-27b", "qwen3.8-max", "qwen3.8-flash");
        assertThat(catalog.primaryModel()).isEqualTo("qwen3.8-27b");
    }

    @Test
    void fallsBackToKnownModelWhenConfigurationIsBlank() {
        ModelCatalog catalog = new ModelCatalog(" ", "");

        assertThat(catalog.orderedModels()).containsExactly("qwen3.8-27b");
    }
}
