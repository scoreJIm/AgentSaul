package com.agentsaul.rag;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid retrieval combining keyword overlap and embedding similarity scores.
 * <p>
 * The final score is a weighted combination:
 * <pre>
 *   score = keywordWeight * keywordScore + embeddingWeight * embeddingScore
 * </pre>
 * <p>
 * Falls back to keyword-only retrieval when the embedding model is unavailable.
 */
@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final RagProperties ragProperties;

    private final DistributionSummary hybridScoreSummary;

    public HybridRetrievalService(EmbeddingService embeddingService,
                                  VectorSearchService vectorSearchService,
                                  RagProperties ragProperties,
                                  MeterRegistry meterRegistry) {
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.ragProperties = ragProperties;
        this.hybridScoreSummary = DistributionSummary.builder("agentsaul.rag.hybrid.score")
                .description("Hybrid retrieval combined scores")
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(meterRegistry);
    }

    /**
     * Perform hybrid retrieval combining keyword and embedding scores.
     *
     * @param query           the user query
     * @param queryKeywords   tokenized keywords from the query (for keyword scoring)
     * @param documentVectors all document vectors (chunk text + embedding)
     * @param topK            maximum number of results
     * @return top-K search results sorted by combined score descending
     */
    public List<VectorSearchService.SearchResult> search(String query,
                                                          List<String> queryKeywords,
                                                          List<VectorSearchService.DocumentVector> documentVectors,
                                                          int topK) {
        if (documentVectors == null || documentVectors.isEmpty()) {
            return List.of();
        }

        double keywordWeight = ragProperties.getRetrieval().getKeywordWeight();
        double embeddingWeight = ragProperties.getRetrieval().getEmbeddingWeight();

        // Compute keyword scores for all documents
        Map<Integer, Double> keywordScores = computeKeywordScores(queryKeywords, documentVectors);

        // Try embedding-based search
        Map<Integer, Double> embeddingScores = Map.of();
        if (embeddingService.isAvailable()) {
            try {
                float[] queryEmbedding = embeddingService.embedQuery(query);
                List<VectorSearchService.SearchResult> embeddingResults =
                        vectorSearchService.search(queryEmbedding, documentVectors, documentVectors.size());
                embeddingScores = embeddingResults.stream()
                        .collect(Collectors.toMap(
                                VectorSearchService.SearchResult::index,
                                r -> (double) r.score()
                        ));
            } catch (Exception e) {
                log.warn("Embedding search failed, falling back to keyword-only: {}", e.getMessage());
            }
        }

        // Combine scores
        List<VectorSearchService.SearchResult> combined = new ArrayList<>();
        for (VectorSearchService.DocumentVector doc : documentVectors) {
            double kwScore = keywordScores.getOrDefault(doc.index(), 0.0);
            double embScore = embeddingScores.getOrDefault(doc.index(), 0.0);

            // Normalize keyword score: divide by max keyword score in this query
            // Raw keyword count can be arbitrarily high, so we sigmoid-normalize
            double normalizedKwScore = sigmoid(kwScore / Math.max(1.0, queryKeywords.size()));

            double combinedScore;
            if (embeddingScores.isEmpty()) {
                // Fallback: keyword-only
                combinedScore = normalizedKwScore;
            } else {
                combinedScore = keywordWeight * normalizedKwScore + embeddingWeight * embScore;
            }

            hybridScoreSummary.record(combinedScore);

            combined.add(new VectorSearchService.SearchResult(
                    doc.chunkText(), (float) combinedScore, doc.index()));
        }

        // Sort by score descending, take top K
        combined.sort(Comparator.comparingDouble(VectorSearchService.SearchResult::score).reversed());

        int limit = Math.min(topK, combined.size());
        List<VectorSearchService.SearchResult> top = new ArrayList<>(combined.subList(0, limit));

        log.debug("Hybrid retrieval: {} results from {} docs (strategy={}, embedding_available={})",
                top.size(), documentVectors.size(),
                embeddingScores.isEmpty() ? "keyword-only" : "hybrid",
                embeddingService.isAvailable());

        return top;
    }

    /**
     * Compute keyword overlap scores for all document vectors.
     * Counts occurrences of each keyword in the chunk text.
     */
    private Map<Integer, Double> computeKeywordScores(List<String> keywords,
                                                       List<VectorSearchService.DocumentVector> documents) {
        Map<Integer, Double> scores = new HashMap<>();
        if (keywords == null || keywords.isEmpty()) {
            return scores;
        }

        for (VectorSearchService.DocumentVector doc : documents) {
            String text = doc.chunkText().toLowerCase();
            double score = 0.0;
            for (String kw : keywords) {
                int idx = 0;
                while ((idx = text.indexOf(kw, idx)) != -1) {
                    score += 1.0;
                    idx += kw.length();
                }
            }
            if (score > 0.0) {
                scores.put(doc.index(), score);
            }
        }
        return scores;
    }

    /**
     * Sigmoid normalization to bound score in [0, 1).
     * For input x >= 0, returns value in [0, 0.5) to [0.5, 1.0).
     */
    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
