package com.agentsaul.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Three chunking strategies for prompt engineering demonstration.
 * <p>
 * Each strategy splits the same source documents differently,
 * showing how chunking affects retrieval quality and LLM responses.
 */
public class ChunkingStrategies {

    private static final Logger log = LoggerFactory.getLogger(ChunkingStrategies.class);

    // ---- Strategy 1: Token-based (fixed window + overlap) ----

    public static final String TOKEN = "token";

    /**
     * Fixed-size chunking with overlap, the most common RAG approach.
     * Splits by token count, keeping a sliding overlap to preserve
     * context across chunk boundaries.
     */
    public static TextSplitter tokenSplitter() {
        return new TokenTextSplitter(
                400,  // defaultChunkSize (Chinese ~200 chars per chunk)
                80,   // minChunkSizeChars
                50,   // minChunkLengthToEmbed (ignore tiny fragments)
                50,   // maxNumChunks
                true  // keepSeparator — retain heading context
        );
    }

    // ---- Strategy 2: Sentence-based (semantic boundary) ----

    public static final String SENTENCE = "sentence";

    private static final Pattern SENTENCE_BOUNDARY =
            Pattern.compile("(?<=[。！？；\\n])\\s*");

    /**
     * Splits by sentence boundaries, keeping paragraphs that are
     * naturally short together. Better semantic coherence than
     * token windows but can produce uneven chunk sizes.
     */
    public static List<Document> sentenceSplit(List<Document> sourceDocs) {
        List<Document> chunks = new ArrayList<>();
        for (Document doc : sourceDocs) {
            String text = doc.getText();
            String[] sentences = SENTENCE_BOUNDARY.split(text);
            StringBuilder buf = new StringBuilder();
            int chunkIdx = 0;

            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (trimmed.isEmpty()) continue;

                // accumulate sentences until ~300 chars
                if (buf.length() + trimmed.length() > 300 && !buf.isEmpty()) {
                    chunks.add(deriveChunk(doc, buf.toString(), "sent", chunkIdx++));
                    buf.setLength(0);
                }
                if (!buf.isEmpty()) buf.append('\n');
                buf.append(trimmed);
            }
            // last partial chunk
            if (!buf.isEmpty()) {
                chunks.add(deriveChunk(doc, buf.toString(), "sent", chunkIdx));
            }
        }
        return chunks;
    }

    // ---- Strategy 3: Paragraph-based (document structure) ----

    public static final String PARAGRAPH = "paragraph";

    private static final Pattern PARAGRAPH_BOUNDARY =
            Pattern.compile("\\n\\s*\\n");

    /**
     * Splits by blank lines (paragraph boundaries). Preserves the
     * original document structure best, but chunks can be large
     * and uneven — some paragraphs are 100 chars, others 800.
     */
    public static List<Document> paragraphSplit(List<Document> sourceDocs) {
        List<Document> chunks = new ArrayList<>();
        for (Document doc : sourceDocs) {
            String[] paragraphs = PARAGRAPH_BOUNDARY.split(doc.getText());
            int chunkIdx = 0;
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (trimmed.isEmpty()) continue;

                // If a paragraph is very long, split it further
                if (trimmed.length() > 500) {
                    // sub-split long paragraphs by sentence
                    String[] subParts = SENTENCE_BOUNDARY.split(trimmed);
                    StringBuilder buf = new StringBuilder();
                    int subIdx = 0;
                    for (String part : subParts) {
                        String t = part.trim();
                        if (t.isEmpty()) continue;
                        if (buf.length() + t.length() > 400 && !buf.isEmpty()) {
                            chunks.add(deriveChunk(doc, buf.toString(), "para", subIdx++));
                            buf.setLength(0);
                        }
                        if (!buf.isEmpty()) buf.append('\n');
                        buf.append(t);
                    }
                    if (!buf.isEmpty()) {
                        chunks.add(deriveChunk(doc, buf.toString(), "para", subIdx));
                    }
                } else {
                    chunks.add(deriveChunk(doc, trimmed, "para", chunkIdx++));
                }
            }
        }
        return chunks;
    }

    // ---- Helpers ----

    private static Document deriveChunk(Document source, String content,
                                        String strategy, int idx) {
        Document chunk = new Document(content, source.getMetadata());
        chunk.getMetadata().put("chunk_strategy", strategy);
        chunk.getMetadata().put("chunk_index", idx);
        chunk.getMetadata().put("source", source.getMetadata().getOrDefault("source", "unknown"));
        return chunk;
    }

    private ChunkingStrategies() {}
}
