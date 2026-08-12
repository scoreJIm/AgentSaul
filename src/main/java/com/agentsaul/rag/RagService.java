package com.agentsaul.rag;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RAG service — hybrid retrieval (keyword + embedding) with SSE streaming.
 * <p>
 * Flow: user query → hybrid retrieval (keyword + vector similarity) → top-K →
 * build augmented prompt → stream LLM response.
 * <p>
 * Embeddings are computed on document load and cached in {@link EmbeddingService}.
 * {@link HybridRetrievalService} combines keyword overlap scores with embedding
 * cosine similarity for semantic search. Falls back to keyword-only when the
 * embedding model is unavailable.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final RagDocumentLoader documentLoader;
    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final HybridRetrievalService hybridRetrievalService;
    private final DocumentStore documentStore;
    private final RagProperties ragProperties;

    private final Timer embeddingDurationTimer;

    static final String RAG_SYSTEM_PROMPT = """
            你是一个法律知识助手。请严格根据以下【检索到的法律知识】来回答用户问题。

            ## 重要规则
            - 仅使用下面提供的法律知识来回答，不要使用你自身的知识
            - 如果检索到的知识不足以回答，请直接说"根据现有法律知识库，我无法确切回答这个问题"
            - 回答时引用知识来源（如"根据合同法..."、"根据刑法..."等）
            - 用中文回答，保持专业但易懂

            ## 检索到的法律知识
            %s

            ## 用户问题
            %s""";

    public RagService(RagDocumentLoader documentLoader,
                      ChatClient.Builder chatClientBuilder,
                      EmbeddingService embeddingService,
                      VectorSearchService vectorSearchService,
                      HybridRetrievalService hybridRetrievalService,
                      DocumentStore documentStore,
                      RagProperties ragProperties,
                      MeterRegistry meterRegistry) {
        this.documentLoader = documentLoader;
        this.chatClient = chatClientBuilder.build();
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.documentStore = documentStore;
        this.ragProperties = ragProperties;

        this.embeddingDurationTimer = Timer.builder("agentsaul.rag.embedding.duration")
                .description("Time taken for document embedding")
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(meterRegistry);
    }

    @PostConstruct
    void init() {
        embedLoadedDocuments();
    }

    // ---- Embedding initialization ----

    /**
     * Embed all documents loaded by {@link RagDocumentLoader} and store them
     * in {@link DocumentStore}. Called on startup and on reindex.
     */
    private void embedLoadedDocuments() {
        long startNs = System.nanoTime();

        List<Document> classpathChunks = documentLoader.getChunks(ChunkingStrategies.TOKEN);
        if (classpathChunks.isEmpty()) {
            log.info("No classpath documents to embed");
            return;
        }

        // Build chunk list from classpath documents
        List<String> chunkTexts = classpathChunks.stream()
                .map(Document::getText)
                .collect(Collectors.toList());

        List<float[]> embeddings = embeddingService.embedDocuments(chunkTexts);

        // Store in DocumentStore under a virtual filename
        List<DocumentStore.ChunkEmbedding> entries = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            Document doc = classpathChunks.get(i);
            float[] emb = i < embeddings.size() ? embeddings.get(i) : new float[embeddingService.embedQuery("").length];
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            entries.add(new DocumentStore.ChunkEmbedding(chunkTexts.get(i), emb, metadata));
        }

        documentStore.addDocument("_classpath_docs", entries);
        documentStore.persist();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        embeddingDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);

        log.info("Embedded {} classpath chunks in {}ms", chunkTexts.size(), durationMs);
    }

    // ---- Retrieval ----

    /**
     * Retrieve relevant chunks using the configured retrieval strategy.
     * <p>
     * Supports three strategies:
     * <ul>
     *   <li><b>keyword</b> — pure keyword overlap (legacy behavior)</li>
     *   <li><b>embedding</b> — pure cosine similarity on embeddings</li>
     *   <li><b>hybrid</b> — weighted combination of keyword + embedding</li>
     * </ul>
     */
    public List<Document> retrieveRelevantChunks(String query, int topK) {
        String strategy = ragProperties.getRetrieval().getStrategy();

        return switch (strategy) {
            case "embedding" -> retrieveByEmbedding(query, topK);
            case "keyword" -> retrieveByKeyword(query, topK);
            default -> retrieveHybrid(query, topK);
        };
    }

    /**
     * Legacy keyword-overlap retrieval (kept for backward compatibility).
     */
    public List<Document> retrieve(String query, int topK) {
        return retrieveRelevantChunks(query, topK);
    }

    private List<Document> retrieveByKeyword(String query, int topK) {
        List<Document> allChunks = documentLoader.getChunks("token");
        if (allChunks.isEmpty()) return List.of();

        List<String> keywords = tokenize(query);
        if (keywords.isEmpty()) return List.of();

        List<ScoredDoc> scored = new ArrayList<>();
        for (Document chunk : allChunks) {
            String text = chunk.getText().toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                int idx = 0;
                while ((idx = text.indexOf(kw, idx)) != -1) {
                    score++;
                    idx += kw.length();
                }
            }
            if (score > 0) {
                scored.add(new ScoredDoc(chunk, score));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        return scored.stream()
                .limit(topK)
                .map(sd -> {
                    Document doc = sd.doc;
                    doc.getMetadata().put("score", sd.score);
                    return doc;
                })
                .collect(Collectors.toList());
    }

    private List<Document> retrieveByEmbedding(String query, int topK) {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding model unavailable, falling back to keyword retrieval");
            return retrieveByKeyword(query, topK);
        }

        float[] queryEmbedding = embeddingService.embedQuery(query);
        List<VectorSearchService.DocumentVector> docVectors = buildDocumentVectors();
        List<VectorSearchService.SearchResult> results =
                vectorSearchService.search(queryEmbedding, docVectors, topK);

        return searchResultsToDocuments(results);
    }

    private List<Document> retrieveHybrid(String query, int topK) {
        List<String> keywords = tokenize(query);
        List<VectorSearchService.DocumentVector> docVectors = buildDocumentVectors();

        List<VectorSearchService.SearchResult> results =
                hybridRetrievalService.search(query, keywords, docVectors, topK);

        return searchResultsToDocuments(results);
    }

    /**
     * Build a unified document vector list from both classpath chunks (in DocumentLoader)
     * and file-system documents (in DocumentStore).
     */
    private List<VectorSearchService.DocumentVector> buildDocumentVectors() {
        List<VectorSearchService.DocumentVector> vectors = new ArrayList<>();

        // Classpath documents: embeddings from the EmbeddingService cache
        List<Document> classpathChunks = documentLoader.getChunks(ChunkingStrategies.TOKEN);
        List<String> chunkTexts = classpathChunks.stream().map(Document::getText).toList();
        List<float[]> classpathEmbeddings = embeddingService.embedDocuments(chunkTexts);

        for (int i = 0; i < classpathChunks.size(); i++) {
            float[] emb = i < classpathEmbeddings.size() ? classpathEmbeddings.get(i) : new float[0];
            vectors.add(new VectorSearchService.DocumentVector(
                    classpathChunks.get(i).getText(), emb, vectors.size()));
        }

        // File-system documents from DocumentStore
        for (DocumentStore.ChunkEmbedding ce : documentStore.getAllChunks()) {
            if (ce.embedding() == null) continue;
            vectors.add(new VectorSearchService.DocumentVector(
                    ce.text(), ce.embedding(), vectors.size()));
        }

        return vectors;
    }

    private List<Document> searchResultsToDocuments(List<VectorSearchService.SearchResult> results) {
        return results.stream()
                .map(r -> {
                    Document doc = new Document(r.documentChunk());
                    doc.getMetadata().put("score", r.score());
                    doc.getMetadata().put("index", r.index());
                    return doc;
                })
                .collect(Collectors.toList());
    }

    // ---- Reindex ----

    /**
     * Re-index all documents: clear cache, reload from classpath, re-embed.
     * This invalidates all cached embeddings and rebuilds the document store.
     */
    public Map<String, Object> reindex() {
        long startNs = System.nanoTime();

        embeddingService.invalidateCache();
        documentStore.clear();

        // Reload and re-embed classpath documents
        embedLoadedDocuments();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        embeddingDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("durationMs", durationMs);
        result.put("documents", documentStore.getDocumentCount());
        result.put("chunks", documentStore.getChunkCount());
        result.put("embeddingAvailable", embeddingService.isAvailable());

        log.info("Reindex complete: {} docs, {} chunks in {}ms",
                documentStore.getDocumentCount(), documentStore.getChunkCount(), durationMs);
        return result;
    }

    /**
     * Add a new document from raw content. The content is chunked and embedded.
     *
     * @param filename the document filename
     * @param content  the raw text content
     * @return summary of the operation
     */
    public Map<String, Object> addDocument(String filename, String content) {
        long startNs = System.nanoTime();

        // Parse content into chunks using paragraph splitting
        List<Document> rawDocs = List.of(new Document(content));
        List<Document> chunks = ChunkingStrategies.paragraphSplit(rawDocs);

        // Also token split for longer documents
        if (content.length() > 1000) {
            chunks = ChunkingStrategies.sentenceSplit(rawDocs);
        }

        List<String> chunkTexts = chunks.stream().map(Document::getText).collect(Collectors.toList());
        List<float[]> embeddings = embeddingService.embedDocuments(chunkTexts);

        List<DocumentStore.ChunkEmbedding> entries = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", filename);
            metadata.put("chunk_strategy", content.length() > 1000 ? "sentence" : "paragraph");
            metadata.put("chunk_index", i);
            entries.add(new DocumentStore.ChunkEmbedding(
                    chunkTexts.get(i),
                    i < embeddings.size() ? embeddings.get(i) : new float[0],
                    metadata));
        }

        documentStore.addDocument(filename, entries);
        documentStore.persist();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("filename", filename);
        result.put("chunks", chunkTexts.size());
        result.put("durationMs", durationMs);
        result.put("embeddingAvailable", embeddingService.isAvailable());

        log.info("Added document '{}': {} chunks in {}ms", filename, chunkTexts.size(), durationMs);
        return result;
    }

    /**
     * Remove a document by filename.
     *
     * @param filename the document filename to remove
     * @return summary of the operation
     */
    public Map<String, Object> removeDocument(String filename) {
        boolean existed = documentStore.removeDocument(filename);
        if (existed) {
            embeddingService.invalidateCache();
            documentStore.persist();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", existed ? "removed" : "not_found");
        result.put("filename", filename);
        return result;
    }

    // ---- RAG chat pipeline (SSE streaming) ----

    public Flux<String> chat(String userQuery, int topK) {
        List<Document> chunks = retrieveRelevantChunks(userQuery, topK);

        String context = chunks.isEmpty()
                ? "(未检索到相关法律知识)"
                : buildContextString(chunks);

        String augmentedPrompt = String.format(RAG_SYSTEM_PROMPT, context, userQuery);

        String chunksJson = chunksToJson(chunks);
        String promptJson = escapeJson(augmentedPrompt);

        return Flux.concat(
                Flux.just("event: chunks\ndata: " + chunksJson + "\n\n"),
                Flux.just("event: prompt\ndata: " + promptJson + "\n\n"),
                Flux.just("event: answer\ndata: \n\n"),
                chatClient.prompt()
                        .system(augmentedPrompt)
                        .user(userQuery)
                        .stream()
                        .content()
                        .map(token -> "data: " + escapeJson(token) + "\n\n"),
                Flux.just("event: done\ndata: {}\n\n")
        );
    }

    // ---- Stats & preview ----

    /**
     * Enhanced stats including embedding and retrieval information.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("documents", documentLoader.getRawDocuments().size() + documentStore.getDocumentCount());
        stats.put("chunks", documentStore.getChunkCount());
        stats.put("totalEmbeddingSize", documentStore.getTotalEmbeddingSize());
        stats.put("retrievalStrategy", ragProperties.getRetrieval().getStrategy());
        stats.put("embeddingAvailable", embeddingService.isAvailable());
        stats.put("embeddingCacheSize", embeddingService.getCacheSize());
        stats.put("embeddedDocCount", documentStore.getDocumentCount());
        stats.put("strategies", documentLoader.getChunksByStrategy().entrySet().stream()
                .map(e -> Map.of(
                        "name", e.getKey(),
                        "chunks", e.getValue().size(),
                        "avgSize", avgChunkSize(e.getValue())))
                .toList());
        stats.put("fileSystemDocuments", documentStore.listDocuments());
        return stats;
    }

    public List<Document> previewChunks(String strategy) {
        return documentLoader.getChunks(strategy);
    }

    // ---- Tokenization (Chinese/English keyword extraction) ----

    /**
     * Split Chinese/English query into keywords for matching.
     * For Chinese: generate overlapping bigrams + keep original words.
     * For English: split by whitespace and punctuation.
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String[] words = text.split("[\\s，。！？；：、（）《》\"'\\[\\]{}()<>.,!?;:]+");
        for (String word : words) {
            if (word.isBlank()) continue;
            String trimmed = word.trim();
            tokens.add(trimmed.toLowerCase());
            if (trimmed.length() >= 2 && containsChinese(trimmed)) {
                for (int i = 0; i < trimmed.length() - 1; i++) {
                    tokens.add(trimmed.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    private boolean containsChinese(String s) {
        return s.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
    }

    // ---- Helpers ----

    private String buildContextString(List<Document> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Document c = chunks.get(i);
            String source = (String) c.getMetadata().getOrDefault("source", "unknown");
            Object score = c.getMetadata().getOrDefault("score", 0);
            sb.append(String.format("[%d] 来源:%s 匹配分:%s\n%s\n\n",
                    i + 1, source, score, c.getText()));
        }
        return sb.toString();
    }

    private String chunksToJson(List<Document> chunks) {
        if (chunks.isEmpty()) return "[]";
        return "[" + chunks.stream()
                .map(c -> {
                    String source = (String) c.getMetadata().getOrDefault("source", "?");
                    Object score = c.getMetadata().getOrDefault("score", 0);
                    return String.format(
                            "{\"source\":\"%s\",\"score\":%s,\"content\":\"%s\"}",
                            escapeJson(source), score, escapeJson(c.getText()));
                })
                .collect(Collectors.joining(",")) + "]";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private int avgChunkSize(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0;
        return (int) chunks.stream().mapToInt(c -> c.getText().length()).average().orElse(0);
    }

    private record ScoredDoc(Document doc, int score) {}
}
