package com.agentsaul.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChunkingStrategies")
class ChunkingStrategiesTest {

    private Document createDoc(String source, String text) {
        return new Document(text, Map.of("source", source));
    }

    @Nested
    @DisplayName("Sentence splitting")
    class SentenceSplit {

        @Test
        @DisplayName("should split by Chinese sentence boundaries")
        void shouldSplitChineseSentences() {
            Document doc = createDoc("test.md",
                    "第一条 合同双方应诚实守信。第二条 任何一方不得擅自变更合同内容。" +
                    "第三条 争议应通过协商解决。");

            List<Document> chunks = ChunkingStrategies.sentenceSplit(List.of(doc));

            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allMatch(c ->
                    c.getMetadata().get("chunk_strategy").equals("sent"));
            assertThat(chunks).allMatch(c ->
                    c.getMetadata().get("source").equals("test.md"));
        }

        @Test
        @DisplayName("should handle empty document list")
        void shouldHandleEmptyList() {
            List<Document> chunks = ChunkingStrategies.sentenceSplit(List.of());
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("should handle single short sentence")
        void shouldHandleShortText() {
            Document doc = createDoc("short.md", "简短文本。");
            List<Document> chunks = ChunkingStrategies.sentenceSplit(List.of(doc));
            assertThat(chunks).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Paragraph splitting")
    class ParagraphSplit {

        @Test
        @DisplayName("should split by blank lines")
        void shouldSplitByParagraphs() {
            Document doc = createDoc("law.md",
                    "第一章 总则\n\n第一条 本法适用于所有民事活动。\n\n" +
                            "第二章 合同\n\n第一条 合同是平等主体之间的协议。");

            List<Document> chunks = ChunkingStrategies.paragraphSplit(List.of(doc));

            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allMatch(c ->
                    c.getMetadata().get("chunk_strategy").equals("para"));
            assertThat(chunks).allMatch(c ->
                    c.getMetadata().get("source").equals("law.md"));
        }

        @Test
        @DisplayName("should handle long paragraphs by sub-splitting")
        void shouldSubSplitLongParagraphs() {
            StringBuilder longPara = new StringBuilder();
            for (int i = 1; i <= 55; i++) {
                longPara.append("第").append(i).append("条规定了相关内容。");
            }
            Document doc = createDoc("long.md", longPara.toString());

            List<Document> chunks = ChunkingStrategies.paragraphSplit(List.of(doc));

            assertThat(chunks).isNotEmpty();
            // Long paragraph should be split into multiple chunks
            assertThat(chunks.size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("Chunk metadata")
    class ChunkMetadata {

        @Test
        @DisplayName("should preserve source in derived chunks")
        void shouldPreserveSource() {
            Document doc = createDoc("contract-law.md", "测试内容。继续写。");
            List<Document> chunks = ChunkingStrategies.sentenceSplit(List.of(doc));

            assertThat(chunks).allMatch(c -> "contract-law.md".equals(
                    c.getMetadata().get("source")));
        }

        @Test
        @DisplayName("should include chunk index in metadata")
        void shouldIncludeChunkIndex() {
            Document doc = createDoc("test.md",
                    "第一段内容。第二段不同内容。第三段又有变化。第四段结束。");
            List<Document> chunks = ChunkingStrategies.sentenceSplit(List.of(doc));

            if (chunks.size() > 1) {
                assertThat(chunks.get(0).getMetadata().get("chunk_index")).isEqualTo(0);
                assertThat(chunks.get(1).getMetadata().get("chunk_index")).isEqualTo(1);
            }
        }
    }
}
