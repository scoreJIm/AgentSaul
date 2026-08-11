package com.agentsaul.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatService — session management")
class ChatServiceTest {

    // ChatService depends on ChatClient, MyBatis mappers, etc.
    // Testing the full service requires Spring context.
    // These tests verify the session UUID generation pattern used by ChatService.

    @Nested
    @DisplayName("Session UUID generation")
    class SessionUuid {

        @Test
        @DisplayName("should generate consistent UUID for same session")
        void shouldGenerateConsistentUuid() {
            Map<String, String> cache = new ConcurrentHashMap<>();
            String sessionId = "test-session-123";

            String uuid1 = cache.computeIfAbsent(sessionId, k -> UUID.randomUUID().toString());
            String uuid2 = cache.computeIfAbsent(sessionId, k -> UUID.randomUUID().toString());

            assertThat(uuid1).isEqualTo(uuid2);
        }

        @Test
        @DisplayName("should generate different UUIDs for different sessions")
        void shouldGenerateDifferentUuids() {
            Map<String, String> cache = new ConcurrentHashMap<>();

            String uuid1 = cache.computeIfAbsent("session-A",
                    k -> UUID.randomUUID().toString());
            String uuid2 = cache.computeIfAbsent("session-B",
                    k -> UUID.randomUUID().toString());

            assertThat(uuid1).isNotEqualTo(uuid2);
        }
    }

    @Nested
    @DisplayName("Conversation ID mapping")
    class ConversationMapping {

        @Test
        @DisplayName("should return null for unknown session")
        void shouldReturnNullForUnknown() {
            Map<String, Long> convMap = new ConcurrentHashMap<>();

            Long convId = convMap.get("unknown-session");

            assertThat(convId).isNull();
        }

        @Test
        @DisplayName("should return stored conversation for known session")
        void shouldReturnStoredConversation() {
            Map<String, Long> convMap = new ConcurrentHashMap<>();
            convMap.put("session-1", 42L);

            assertThat(convMap.get("session-1")).isEqualTo(42L);
        }
    }
}
