package com.agentsaul.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Loads legal knowledge documents from classpath and applies chunking strategies.
 * <p>
 * No embedding API involved — pure in-memory document processing.
 * Chunks are stored in-memory and searched via keyword matching in RagService.
 */
@Component
public class RagDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentLoader.class);

    private final TextSplitter tokenSplitter;

    private final List<Document> rawDocuments = new ArrayList<>();
    private final Map<String, List<Document>> chunksByStrategy = new LinkedHashMap<>();

    public RagDocumentLoader() {
        this.tokenSplitter = ChunkingStrategies.tokenSplitter();
    }

    @PostConstruct
    void init() {
        loadDocuments();
        chunkAll();
        log.info("RAG loaded: {} docs, strategies={}", rawDocuments.size(), chunksByStrategy.keySet());
    }

    private void loadDocuments() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:rag-docs/*.md");
            for (Resource res : resources) {
                var reader = new TextReader(res);
                List<Document> docs = reader.get();
                for (Document doc : docs) {
                    doc.getMetadata().put("source", res.getFilename());
                    rawDocuments.add(doc);
                    log.info("RAG loaded: {}", res.getFilename());
                }
            }
        } catch (Exception e) {
            log.error("RAG failed to load documents: {}", e.getMessage());
        }
    }

    private void chunkAll() {
        if (rawDocuments.isEmpty()) return;
        chunksByStrategy.put(ChunkingStrategies.TOKEN, tokenSplitter.apply(rawDocuments));
        chunksByStrategy.put(ChunkingStrategies.SENTENCE, ChunkingStrategies.sentenceSplit(rawDocuments));
        chunksByStrategy.put(ChunkingStrategies.PARAGRAPH, ChunkingStrategies.paragraphSplit(rawDocuments));
        chunksByStrategy.forEach((s, chunks) -> log.info("RAG strategy [{}]: {} chunks", s, chunks.size()));
    }

    public List<Document> getRawDocuments() { return Collections.unmodifiableList(rawDocuments); }
    public Map<String, List<Document>> getChunksByStrategy() { return Collections.unmodifiableMap(chunksByStrategy); }

    public List<Document> getChunks(String strategy) {
        return chunksByStrategy.getOrDefault(strategy, List.of());
    }
}
