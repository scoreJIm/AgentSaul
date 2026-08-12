package com.agentsaul.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic mock embedding model for development and testing.
 * <p>
 * Generates hash-based normalized embedding vectors from text input,
 * producing consistent results for the same input across restarts.
 * All vectors are L2-normalized (unit length) for valid cosine similarity.
 * <p>
 * This is used as a fallback when no real embedding API key is configured.
 */
public class MockEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(MockEmbeddingModel.class);

    private final int dimension;

    public MockEmbeddingModel(int dimension) {
        this.dimension = dimension;
        log.info("MockEmbeddingModel initialized with dimension={}", dimension);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            float[] vector = generateEmbedding(texts.get(i));
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return generateEmbedding(document.getText());
    }

    @Override
    public int dimensions() {
        return dimension;
    }

    /**
     * Generates a deterministic, normalized embedding vector from text.
     * Uses hashCode as the RNG seed for reproducibility, Gaussian-distributed
     * values, and L2 normalization to produce unit vectors.
     */
    private float[] generateEmbedding(String text) {
        float[] vec = new float[dimension];
        int hash = text.hashCode();
        Random rng = new Random(hash);
        float sumSq = 0.0f;
        for (int i = 0; i < dimension; i++) {
            vec[i] = (float) rng.nextGaussian();
            sumSq += vec[i] * vec[i];
        }
        // L2 normalize to unit vector for valid cosine similarity
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 0.0f) {
            for (int i = 0; i < dimension; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }
}
