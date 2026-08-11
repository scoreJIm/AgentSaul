package com.agentsaul.rag;

import org.springframework.context.annotation.Configuration;

/**
 * RAG configuration.
 * <p>
 * This demo uses local keyword-based retrieval — no embedding API, no vector DB.
 * The goal is to demonstrate the prompt engineering pipeline (chunk → retrieve →
 * augment prompt → LLM) without external dependencies slowing things down.
 */
@Configuration
public class RagConfig {
    // Keyword-based retrieval needs no special beans.
    // All logic is in ChunkingStrategies + RagDocumentLoader + RagService.
}
