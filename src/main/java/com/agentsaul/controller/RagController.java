package com.agentsaul.controller;

import com.agentsaul.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
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
    public Flux<String> chat(@RequestBody Map<String, Object> body) {
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
    public List<Document> previewChunks(@RequestParam(defaultValue = "token") String strategy) {
        log.info("[RAG] GET /chunks strategy={}", strategy);
        return ragService.previewChunks(strategy);
    }

    /**
     * Knowledge base stats: doc count, chunk counts per strategy, indexing status.
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        log.info("[RAG] GET /stats");
        return ragService.getStats();
    }

}
