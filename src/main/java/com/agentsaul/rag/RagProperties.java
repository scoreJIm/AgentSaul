package com.agentsaul.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the RAG module.
 * <p>
 * Binds to the {@code agentsaul.rag} namespace in application.yml.
 */
@ConfigurationProperties(prefix = "agentsaul.rag")
public class RagProperties {

    private final Retrieval retrieval = new Retrieval();
    private final Embedding embedding = new Embedding();

    public Retrieval getRetrieval() { return retrieval; }
    public Embedding getEmbedding() { return embedding; }

    public static class Retrieval {
        /** Retrieval strategy: hybrid, embedding, or keyword */
        private String strategy = "hybrid";
        /** Weight for keyword overlap score in hybrid mode */
        private double keywordWeight = 0.3;
        /** Weight for embedding similarity score in hybrid mode */
        private double embeddingWeight = 0.7;
        /** Default number of top results to return */
        private int topK = 5;

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }

        public double getKeywordWeight() { return keywordWeight; }
        public void setKeywordWeight(double keywordWeight) { this.keywordWeight = keywordWeight; }

        public double getEmbeddingWeight() { return embeddingWeight; }
        public void setEmbeddingWeight(double embeddingWeight) { this.embeddingWeight = embeddingWeight; }

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }

    public static class Embedding {
        /** Embedding model name (e.g., text-embedding-v3) */
        private String model = "text-embedding-v3";
        /** Embedding vector dimension */
        private int dimension = 1024;
        /** Batch size for embedding API calls */
        private int batchSize = 20;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }
}
