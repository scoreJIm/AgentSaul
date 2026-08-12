package com.agentsaul.controller;

import com.agentsaul.annotation.RateLimit;
import com.agentsaul.rag.RagService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@Tag(name = "RAG", description = "Retrieval-Augmented Generation — search legal knowledge and augment LLM prompts")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);
    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * RAG streaming chat — SSE with typed events.
     * Events: chunks (retrieved docs), prompt (augmented prompt), answer (LLM), done
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('USER')")
    @RateLimit(limit = 10, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "rag.chat.stream", description = "Time taken for RAG streaming chat")
    @Operation(summary = "RAG chat (streaming)",
            description = "Performs Retrieval-Augmented Generation: retrieves relevant legal documents, "
                    + "augments the LLM prompt with context, and streams the response as SSE events. "
                    + "Events: chunks, prompt, answer, done, error.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream with retrieved chunks, augmented prompt, and LLM answer"),
            @ApiResponse(responseCode = "400", description = "Empty query")
    })
    public Flux<String> chat(
            @RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 3;

        log.info("[RAG] query len={}, topK={}", query.length(), topK);
        if (query.isBlank()) {
            return Flux.just("event: error\ndata: 请输入问题\n\n");
        }
        return ragService.chat(query, topK)
                .onErrorResume(e -> {
                    log.error("[RAG] error: {}", e.getMessage());
                    return Flux.just("event: error\ndata: " + e.getMessage() + "\n\n");
                });
    }

    /**
     * Preview chunks for a given strategy.
     */
    @GetMapping("/chunks")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "rag.chunks.preview", description = "Time taken to preview chunks")
    @Operation(summary = "Preview document chunks",
            description = "Returns document chunks for the specified chunking strategy to help evaluate chunking quality")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of document chunks")
    })
    public List<Document> previewChunks(
            @Parameter(description = "Chunking strategy: token, sentence, or paragraph") @RequestParam(defaultValue = "token") String strategy) {
        log.info("[RAG] GET /chunks strategy={}", strategy);
        return ragService.previewChunks(strategy);
    }

    /**
     * Knowledge base stats: doc count, chunk counts per strategy, indexing status.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "rag.stats", description = "Time taken to retrieve RAG stats")
    @Operation(summary = "Get knowledge base statistics",
            description = "Returns document count, chunk counts per strategy, embedding status, and indexing status for the RAG knowledge base")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stats object with document and chunk counts")
    })
    public Map<String, Object> stats() {
        log.info("[RAG] GET /stats");
        return ragService.getStats();
    }

    /**
     * Re-index all documents: clear cache, reload from classpath, re-embed.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(limit = 5, windowSeconds = 300, scope = RateLimit.Scope.USER)
    @Timed(value = "rag.reindex", description = "Time taken to reindex all documents")
    @Operation(summary = "Re-index all documents",
            description = "Clears embedding cache, reloads all documents from classpath, "
                    + "re-embeds them, and updates the document store. "
                    + "Useful after changing chunking strategies or embedding models.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reindex result with document/chunk counts and duration"),
            @ApiResponse(responseCode = "500", description = "Reindex failure")
    })
    public Map<String, Object> reindex() {
        log.info("[RAG] POST /reindex");
        return ragService.reindex();
    }

    /**
     * Add or update a document in the knowledge base.
     */
    @PutMapping("/documents")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(limit = 10, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "rag.documents.add", description = "Time taken to add a document")
    @Operation(summary = "Add or update a document",
            description = "Adds a new document (or updates an existing one) to the knowledge base. "
                    + "The content is auto-chunked and embedded for semantic search.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document added successfully with chunk count"),
            @ApiResponse(responseCode = "400", description = "Missing filename or content")
    })
    public Map<String, Object> addDocument(@RequestBody Map<String, String> body) {
        String filename = body.get("filename");
        String content = body.get("content");

        log.info("[RAG] PUT /documents filename={}, contentLen={}",
                filename, content != null ? content.length() : 0);

        if (filename == null || filename.isBlank()) {
            return Map.of("status", "error", "message", "filename is required");
        }
        if (content == null || content.isBlank()) {
            return Map.of("status", "error", "message", "content is required");
        }

        return ragService.addDocument(filename, content);
    }

    /**
     * Remove a document from the knowledge base.
     */
    @DeleteMapping("/documents/{filename}")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(limit = 10, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Timed(value = "rag.documents.remove", description = "Time taken to remove a document")
    @Operation(summary = "Remove a document",
            description = "Removes a document and its embeddings from the knowledge base.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document removed successfully"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public Map<String, Object> removeDocument(
            @Parameter(description = "Document filename to remove") @PathVariable String filename) {
        log.info("[RAG] DELETE /documents/{}", filename);
        return ragService.removeDocument(filename);
    }

}
