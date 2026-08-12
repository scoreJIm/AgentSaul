package com.agentsaul.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory document store with disk persistence.
 * <p>
 * Stores documents as filename -> list of (chunk text, embedding) pairs.
 * On startup, loads documents from the configured documents directory and
 * restores index state from disk for fast restart.
 * <p>
 * Persistence format: JSON file at {@code /app/data/rag-index.json} containing
 * filenames and chunk texts (embeddings are recomputed on reload).
 */
@Component
public class DocumentStore {

    private static final Logger log = LoggerFactory.getLogger(DocumentStore.class);

    private static final String INDEX_FILE_PATH = "/app/data/rag-index.json";
    private static final String DOCUMENTS_DIR_PATH = "/app/data/documents";

    private final ObjectMapper objectMapper;

    /** filename -> list of chunk records */
    private final ConcurrentHashMap<String, List<ChunkEmbedding>> store = new ConcurrentHashMap<>();

    public DocumentStore() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Holds a single chunk: its text content and associated embedding vector.
     */
    public record ChunkEmbedding(String text, float[] embedding, Map<String, Object> metadata) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkEmbedding that)) return false;
            return text.equals(that.text);
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }
    }

    @PostConstruct
    void init() {
        loadFromDisk();
    }

    // ---- CRUD operations ----

    /**
     * Add or update a document with its chunk embeddings.
     *
     * @param filename   the document filename (used as key)
     * @param chunks     list of chunk entries (text + embedding)
     */
    public void addDocument(String filename, List<ChunkEmbedding> chunks) {
        store.put(filename, new ArrayList<>(chunks));
        log.info("DocumentStore: added '{}' with {} chunks", filename, chunks.size());
    }

    /**
     * Remove a document and its embeddings.
     *
     * @param filename the document filename to remove
     * @return true if the document existed and was removed
     */
    public boolean removeDocument(String filename) {
        List<ChunkEmbedding> removed = store.remove(filename);
        if (removed != null) {
            log.info("DocumentStore: removed '{}' ({} chunks)", filename, removed.size());
            return true;
        }
        return false;
    }

    /**
     * @return set of all stored document filenames
     */
    public Set<String> listDocuments() {
        return Collections.unmodifiableSet(store.keySet());
    }

    /**
     * @return total number of chunks across all documents
     */
    public int getChunkCount() {
        return store.values().stream().mapToInt(List::size).sum();
    }

    /**
     * @return total number of documents stored
     */
    public int getDocumentCount() {
        return store.size();
    }

    /**
     * @return total size of all embedding arrays in bytes (approximate)
     */
    public long getTotalEmbeddingSize() {
        return store.values().stream()
                .flatMap(List::stream)
                .mapToLong(ce -> ce.embedding() != null ? (long) ce.embedding().length * 4L : 0L)
                .sum();
    }

    /**
     * @return all chunk embeddings across all documents, ordered by filename then index
     */
    public List<ChunkEmbedding> getAllChunks() {
        return store.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());
    }

    /**
     * @return all chunk embeddings for a specific document
     */
    public List<ChunkEmbedding> getChunks(String filename) {
        return store.getOrDefault(filename, List.of());
    }

    /**
     * Clear all documents from the store.
     */
    public void clear() {
        int count = store.size();
        store.clear();
        log.info("DocumentStore: cleared {} documents", count);
    }

    // ---- Persistence ----

    /**
     * Persist the current index state to disk as JSON.
     * Only stores chunk texts (not embeddings, which are recomputed on reload).
     */
    public void persist() {
        try {
            Path indexPath = Path.of(INDEX_FILE_PATH);
            Path parentDir = indexPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Map<String, List<String>> serializable = new LinkedHashMap<>();
            store.forEach((filename, chunks) -> {
                List<String> texts = chunks.stream()
                        .map(ChunkEmbedding::text)
                        .collect(Collectors.toList());
                serializable.put(filename, texts);
            });

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(serializable);
            Files.writeString(indexPath, json);
            log.debug("DocumentStore: persisted index to {} ({} documents)", indexPath, store.size());
        } catch (IOException e) {
            log.error("DocumentStore: failed to persist index: {}", e.getMessage());
        }
    }

    /**
     * Load document index from disk and scan the documents directory for files.
     * On startup, this restores the document listing from the persisted index.
     * Embeddings are NOT loaded from disk — they are recomputed on reindex.
     */
    private void loadFromDisk() {
        // Try to load persisted index
        Path indexPath = Path.of(INDEX_FILE_PATH);
        if (Files.exists(indexPath)) {
            try {
                String json = Files.readString(indexPath);
                Map<String, List<String>> loaded = objectMapper.readValue(
                        json, new TypeReference<Map<String, List<String>>>() {});
                loaded.forEach((filename, texts) -> {
                    List<ChunkEmbedding> entries = texts.stream()
                            .map(t -> new ChunkEmbedding(t, null, Map.of(
                                    "source", filename,
                                    "chunk_strategy", "token"
                            )))
                            .collect(Collectors.toList());
                    store.put(filename, entries);
                });
                log.info("DocumentStore: loaded index from {} ({} documents, {} chunks)",
                        indexPath, store.size(),
                        store.values().stream().mapToInt(List::size).sum());
            } catch (IOException e) {
                log.warn("DocumentStore: failed to load index from {}: {}", indexPath, e.getMessage());
            }
        }

        // Scan documents directory for new files
        Path docsDir = Path.of(DOCUMENTS_DIR_PATH);
        if (Files.exists(docsDir) && Files.isDirectory(docsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(docsDir, "*.md")) {
                for (Path file : stream) {
                    String filename = file.getFileName().toString();
                    if (!store.containsKey(filename)) {
                        String content = Files.readString(file);
                        List<ChunkEmbedding> entries = List.of(
                                new ChunkEmbedding(content, null, Map.of(
                                        "source", filename,
                                        "chunk_strategy", "token"
                                ))
                        );
                        store.put(filename, entries);
                        log.info("DocumentStore: discovered new file '{}'", filename);
                    }
                }
            } catch (IOException e) {
                log.warn("DocumentStore: failed to scan documents directory: {}", e.getMessage());
            }
        } else {
            log.debug("DocumentStore: documents directory not found at {}, skipping file scan", DOCUMENTS_DIR_PATH);
        }
    }
}
