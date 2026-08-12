package com.agentsaul.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG configuration.
 * <p>
 * Registers the embedding model bean (mock if no real one is available),
 * and enables {@link RagProperties} for {@code agentsaul.rag} config binding.
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    /**
     * MockEmbeddingModel as a fallback when no real {@link EmbeddingModel} bean
     * is provided by Spring AI autoconfiguration (e.g., when no API key is set).
     * <p>
     * Uses deterministic hash-based embeddings for testing and development.
     * Dimension is read from {@link RagProperties}.
     */
    @Bean
    @ConditionalOnMissingBean(type = "org.springframework.ai.openai.OpenAiEmbeddingModel")
    public EmbeddingModel mockEmbeddingModel(RagProperties ragProperties) {
        int dimension = ragProperties.getEmbedding().getDimension();
        log.info("No EmbeddingModel bean found — creating MockEmbeddingModel (dimension={})", dimension);
        return new MockEmbeddingModel(dimension);
    }
}
