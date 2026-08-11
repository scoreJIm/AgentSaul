package com.agentsaul.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG service — pure local keyword retrieval, zero API dependency.
 * <p>
 * Flow: user query → keyword extraction → score chunks by overlap → top-K →
 * build augmented prompt → stream LLM response.
 * <p>
 * Every step is exposed via SSE events so the frontend can display
 * exactly what was retrieved and how the prompt was constructed.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final RagDocumentLoader documentLoader;
    private final ChatClient chatClient;

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

    public RagService(RagDocumentLoader documentLoader, ChatClient.Builder chatClientBuilder) {
        this.documentLoader = documentLoader;
        this.chatClient = chatClientBuilder.build();
    }

    // ---- keyword-based retrieval (local, instant) ----

    /**
     * Split Chinese/English query into keywords for matching.
     * For Chinese: generate overlapping bigrams + keep original words.
     * For English: split by whitespace and punctuation.
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        // Split by whitespace and punctuation for mixed CN/EN text
        String[] words = text.split("[\\s，。！？；：、（）《》\"'\\[\\]{}()<>.,!?;:]+");
        for (String word : words) {
            if (word.isBlank()) continue;
            String trimmed = word.trim();
            tokens.add(trimmed.toLowerCase());
            // For Chinese text, also add bigrams for partial matching
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

    /**
     * Score chunks by keyword overlap count.
     * Returns top-K chunks sorted by score descending.
     */
    public List<Document> retrieve(String query, int topK) {
        List<Document> allChunks = documentLoader.getChunks("token");
        if (allChunks.isEmpty()) return List.of();

        List<String> keywords = tokenize(query);
        if (keywords.isEmpty()) return List.of();

        // Score each chunk: count keyword matches
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

        // Sort by score desc, take top K
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

    // ---- RAG chat pipeline (SSE streaming) ----

    public Flux<String> chat(String userQuery, int topK) {
        List<Document> chunks = retrieve(userQuery, topK);

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

    // ---- stats & preview ----

    public Map<String, Object> getStats() {
        return Map.of(
                "documents", documentLoader.getRawDocuments().size(),
                "retrievalMethod", "keyword-overlap (local, no embedding API)",
                "strategies", documentLoader.getChunksByStrategy().entrySet().stream()
                        .map(e -> Map.of(
                                "name", e.getKey(),
                                "chunks", e.getValue().size(),
                                "avgSize", avgChunkSize(e.getValue())))
                        .toList()
        );
    }

    public List<Document> previewChunks(String strategy) {
        return documentLoader.getChunks(strategy);
    }

    // ---- helpers ----

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
