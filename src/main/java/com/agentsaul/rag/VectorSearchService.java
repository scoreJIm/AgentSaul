package com.agentsaul.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Performs cosine similarity search over document embedding vectors.
 * <p>
 * Each document chunk is paired with its pre-computed embedding vector.
 * The search computes cosine similarity between the query embedding and
 * each document vector, then returns the top-K results sorted by score.
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    /**
     * Represents a single document vector entry (chunk text + its embedding).
     */
    public record DocumentVector(String chunkText, float[] embedding, int index) {}

    /**
     * Represents a search result with score and metadata.
     */
    public record SearchResult(String documentChunk, float score, int index) {}

    /**
     * Search for the top-K most similar document chunks to the query embedding
     * using cosine similarity.
     *
     * @param queryEmbedding the query embedding vector
     * @param documents      the list of document vectors to search
     * @param topK           maximum number of results to return
     * @return top-K search results sorted by descending similarity score
     */
    public List<SearchResult> search(float[] queryEmbedding, List<DocumentVector> documents, int topK) {
        if (documents == null || documents.isEmpty() || queryEmbedding == null) {
            return List.of();
        }

        double queryNorm = l2Norm(queryEmbedding);
        if (queryNorm == 0.0) {
            log.debug("Query embedding has zero norm, returning empty results");
            return List.of();
        }

        List<SearchResult> allResults = new ArrayList<>(documents.size());

        for (DocumentVector doc : documents) {
            double similarity = cosineSimilarity(queryEmbedding, doc.embedding());
            allResults.add(new SearchResult(doc.chunkText(), (float) similarity, doc.index()));
        }

        // Sort by score descending, take top K
        allResults.sort(Comparator.comparingDouble(SearchResult::score).reversed());

        int limit = Math.min(topK, allResults.size());
        List<SearchResult> top = allResults.subList(0, limit);

        double topScore = top.isEmpty() ? 0.0 : top.get(0).score();
        log.debug("Vector search returned {} results from {} documents (top score: {})",
                top.size(), documents.size(), String.format("%.4f", topScore));

        return top;
    }

    /**
     * Compute cosine similarity between two vectors.
     *
     * @param a first vector
     * @param b second vector
     * @return cosine similarity in range [-1.0, 1.0], or 0.0 if either vector has zero norm
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0f;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * (double) b[i];
            normA += (double) a[i] * (double) a[i];
            normB += (double) b[i] * (double) b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0f;
        }

        // Clamp to [-1, 1] to handle floating-point imprecision
        double raw = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        return (float) Math.max(-1.0, Math.min(1.0, raw));
    }

    private static double l2Norm(float[] vec) {
        double sum = 0.0;
        for (float v : vec) {
            sum += (double) v * (double) v;
        }
        return Math.sqrt(sum);
    }
}
