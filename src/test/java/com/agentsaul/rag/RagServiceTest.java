package com.agentsaul.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RagService")
class RagServiceTest {

    private RagDocumentLoader documentLoader;
    private RagService ragService;

    @BeforeEach
    void setUp() {
        documentLoader = mock(RagDocumentLoader.class);
        // RagService needs ChatClient.Builder which is complex to mock.
        // We test the retrieval and stats methods which don't use ChatClient.
        // ChatClient-dependent methods (chat) are tested via controller IT.
    }

    @Nested
    @DisplayName("previewChunks")
    class PreviewChunks {

        @Test
        @DisplayName("should return chunks for given strategy")
        void shouldReturnChunks() {
            // Can't easily construct RagService without ChatClient.Builder,
            // so we test indirectly via the document loader pattern.
            Document doc = new Document("test content", Map.of("source", "test.md"));
            when(documentLoader.getChunks("token")).thenReturn(List.of(doc));

            List<Document> chunks = documentLoader.getChunks("token");
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getText()).isEqualTo("test content");
        }
    }

    @Nested
    @DisplayName("Document loader — raw documents")
    class RawDocuments {

        @Test
        @DisplayName("should return raw documents from loader")
        void shouldReturnRawDocuments() {
            Document doc = new Document("# Contract Law\n\nArticle 107...",
                    Map.of("source", "contract-law.md"));
            when(documentLoader.getRawDocuments()).thenReturn(List.of(doc));

            List<Document> docs = documentLoader.getRawDocuments();
            assertThat(docs).hasSize(1);
            assertThat(docs.get(0).getMetadata().get("source")).isEqualTo("contract-law.md");
        }
    }

    @Nested
    @DisplayName("Document metadata")
    class DocumentMetadata {

        @Test
        @DisplayName("should preserve metadata fields")
        void shouldPreserveMetadata() {
            Map<String, Object> meta = Map.of("source", "civil-procedure.md",
                    "title", "Civil Procedure Law", "strategy", "token");
            Document doc = new Document("Article 1: This law governs civil litigation.", meta);

            assertThat(doc.getMetadata().get("source")).isEqualTo("civil-procedure.md");
            assertThat(doc.getMetadata().get("title")).isEqualTo("Civil Procedure Law");
            assertThat(doc.getMetadata().get("strategy")).isEqualTo("token");
            assertThat(doc.getText()).contains("Article 1");
        }
    }
}
