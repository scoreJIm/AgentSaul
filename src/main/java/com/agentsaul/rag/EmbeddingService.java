package com.agentsaul.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages document embeddings with in-memory caching.
 * <p>
 * Wraps Spring AI's {@link EmbeddingModel} to provide batch embedding
 * and query embedding operations. Embeddings are cached by chunk text
 * so repeated requests for the same document chunks do not re-compute.
 * <p>
 * Cache is invalidated on document reload (reindex) via {@link #invalidateCache()}.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;

    /** In-memory cache: chunk text -> embedding vector */
    private final Map<String, float[]> cache = new ConcurrentHashMap<>();

    private volatile boolean available = true;

    public EmbeddingService(EmbeddingModel embeddingModel, RagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.ragProperties = ragProperties;
        log.info("EmbeddingService initialized: model dimension={}, batchSize={}",
                embeddingModel.dimensions(), ragProperties.getEmbedding().getBatchSize());
    }

    /**
     * Generate embeddings for a batch of document chunks.
     * Uses the configured batch size to avoid overwhelming the embedding API.
     *
     * @param chunks list of chunk texts to embed
     * @return list of corresponding embedding vectors (same order)
     */
    public List<float[]> embedDocuments(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<float[]> results = new ArrayList<>(chunks.size());
        int batchSize = ragProperties.getEmbedding().getBatchSize();

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<String> batch = chunks.subList(i, end);

            List<String> textsToEmbed = new ArrayList<>();
            List<Integer> cacheMissIndices = new ArrayList<>();

            for (int j = 0; j < batch.size(); j++) {
                String text = batch.get(j);
                float[] cached = cache.get(text);
                if (cached != null) {
                    results.add(cached);
                } else {
                    textsToEmbed.add(text);
                    cacheMissIndices.add(i + j);
                }
            }

            if (!textsToEmbed.isEmpty()) {
                try {
                    List<float[]> batchEmbeddings = embeddingModel.embed(textsToEmbed);
                    // Map results back to original positions
                    int resultIdx = 0;
                    for (int j = 0; j < batch.size(); j++) {
                        int globalIdx = i + j;
                        if (cacheMissIndices.contains(globalIdx)) {
                            float[] emb = batchEmbeddings.get(resultIdx++);
                            cache.put(batch.get(j), emb);
                            results.add(emb);
                        }
                    }
                } catch (Exception e) {
                    log.error("Embedding API call failed for batch {}-{}: {}", i, end, e.getMessage());
                    available = false;
                    // Return partial results with zero vectors for failed batch
                    for (int j = 0; j < batch.size(); j++) {
                        int globalIdx = i + j;
                        if (cacheMissIndices.contains(globalIdx)) {
                            results.add(new float[embeddingModel.dimensions()]);
                        }
                    }
                }
            }
        }

        log.debug("Embedded {} chunks ({} cache hits, {} API calls)",
                chunks.size(),
                chunks.size() - cacheMissCount(chunks),
                cacheMissCount(chunks));
        return results;
    }

    /**
     * Generate embedding for a single query text.
     * Results are not cached (queries are typically unique).
     *
     * @param query the query text to embed
     * @return the embedding vector
     */
    public float[] embedQuery(String query) {
        if (query == null || query.isBlank()) {
            return new float[embeddingModel.dimensions()];
        }
        try {
            return embeddingModel.embed(query);
        } catch (Exception e) {
            log.error("Query embedding failed: {}", e.getMessage());
            available = false;
            return new float[embeddingModel.dimensions()];
        }
    }

    /**
     * Invalidates the entire embedding cache.
     * Called on document reload / reindex.
     */
    public void invalidateCache() {
        int size = cache.size();
        cache.clear();
        available = true;
        log.info("Embedding cache invalidated ({} entries cleared)", size);
    }

    /**
     * @return true if the embedding model is available and functioning
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * @return the number of cached embeddings
     */
    public int getCacheSize() {
        return cache.size();
    }

    private int cacheMissCount(List<String> chunks) {
        return (int) chunks.stream().filter(t -> !cache.containsKey(t)).count();
    }
}
