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

        int total = chunks.size();
        float[] zeroVec = new float[embeddingModel.dimensions()];
        float[][] results = new float[total][];
        int batchSize = ragProperties.getEmbedding().getBatchSize();

        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            int batchLen = end - i;

            List<String> textsToEmbed = new ArrayList<>(batchLen);
            int[] missGlobalIndices = new int[batchLen];
            int missCount = 0;

            for (int j = 0; j < batchLen; j++) {
                int globalIdx = i + j;
                String text = chunks.get(globalIdx);
                float[] cached = cache.get(text);
                if (cached != null) {
                    results[globalIdx] = cached;
                } else {
                    textsToEmbed.add(text);
                    missGlobalIndices[missCount++] = globalIdx;
                }
            }

            if (missCount > 0) {
                try {
                    List<float[]> batchEmbeddings = embeddingModel.embed(textsToEmbed);
                    for (int k = 0; k < batchEmbeddings.size() && k < missCount; k++) {
                        float[] emb = batchEmbeddings.get(k);
                        int globalIdx = missGlobalIndices[k];
                        cache.put(chunks.get(globalIdx), emb);
                        results[globalIdx] = emb;
                    }
                } catch (Exception e) {
                    log.error("Embedding API call failed for batch {}-{}: {}", i, end, e.getMessage());
                    available = false;
                    for (int k = 0; k < missCount; k++) {
                        results[missGlobalIndices[k]] = zeroVec;
                    }
                }
            }
        }

        // Replace any remaining nulls with zero vectors
        List<float[]> finalResults = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            finalResults.add(results[i] != null ? results[i] : zeroVec);
        }

        log.debug("Embedded {} chunks ({} cache hits, {} API calls)",
                total, total - cacheMissCount(chunks), cacheMissCount(chunks));
        return finalResults;
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
