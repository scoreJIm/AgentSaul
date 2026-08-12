package com.agentsaul.config;

import com.agentsaul.repository.MessageMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    public ChatMemoryFactory chatMemoryFactory(MessageMapper messageMapper) {
        return new ChatMemoryFactory(messageMapper);
    }
}
