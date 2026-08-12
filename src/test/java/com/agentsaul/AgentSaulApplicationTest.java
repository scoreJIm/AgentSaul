package com.agentsaul;

import com.agentsaul.repository.ConversationMapper;
import com.agentsaul.repository.MessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(properties = {
    "spring.ai.openai.api-key=test-dummy-key",
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
        "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration," +
        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration"
})
class AgentSaulApplicationTest {

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private ConversationMapper conversationMapper;

    @MockBean
    private MessageMapper messageMapper;

    @Test
    void contextLoads() {
        // Verify Spring context starts successfully
    }
}
