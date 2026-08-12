package com.agentsaul.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI agentSaulOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AgentSaul API")
                        .version("0.0.1")
                        .description("""
                                Better Call Saul — AI Attorney Chat API.

                                ## Features
                                - **Chat** — AI-powered legal assistant with streaming responses
                                - **RAG** — Retrieval-Augmented Generation for legal knowledge
                                - **MCP** — Model Context Protocol tool demo
                                - **Tools** — Weather, geolocation, legal calculation, translation, search

                                ## Authentication
                                No authentication required for local development.
                                """)
                        .contact(new Contact()
                                .name("AgentSaul Team")
                                .email("dev@agentsaul.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Dev"),
                        new Server().url("https://agentsaul.example.com").description("Production")
                ));
    }
}
