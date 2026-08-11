package com.agentsaul.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagDocumentLoader")
class RagDocumentLoaderTest {

    private RagDocumentLoader loader;

    @BeforeEach
    void setUp() {
        loader = new RagDocumentLoader();
    }

    @Nested
    @DisplayName("Initial state")
    class InitialState {

        @Test
        @DisplayName("should be empty before init")
        void shouldBeEmptyBeforeInit() {
            assertThat(loader.getRawDocuments()).isEmpty();
            assertThat(loader.getChunks("token")).isEmpty();
            assertThat(loader.getChunks("sentence")).isEmpty();
        }

        @Test
        @DisplayName("chunksByStrategy should be empty initially")
        void chunksByStrategyShouldBeEmpty() {
            assertThat(loader.getChunksByStrategy()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getChunks with unknown strategy")
    class UnknownStrategy {

        @Test
        @DisplayName("should return empty list for unknown strategy")
        void shouldReturnEmptyForUnknown() {
            List<Document> chunks = loader.getChunks("nonexistent");
            assertThat(chunks).isEmpty();
        }
    }

    @Nested
    @DisplayName("Document construction")
    class DocumentConstruction {

        @Test
        @DisplayName("should create document with content and metadata")
        void shouldCreateDocument() {
            Document doc = new Document("Test content for RAG",
                    Map.of("source", "test.md", "title", "Test"));

            assertThat(doc.getText()).isEqualTo("Test content for RAG");
            assertThat(doc.getMetadata().get("source")).isEqualTo("test.md");
            assertThat(doc.getMetadata().get("title")).isEqualTo("Test");
        }

        @Test
        @DisplayName("should support Chinese content")
        void shouldSupportChineseContent() {
            Document doc = new Document("合同法第107条规定了违约责任的相关内容。",
                    Map.of("source", "contract-law.md"));

            assertThat(doc.getText()).contains("合同法");
            assertThat(doc.getText()).contains("违约责任");
        }
    }
}
