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
                                A conversational chat API built on Spring AI: function calling, SSE
                                streaming, conversation memory, and structured output.
                                Tools: weather, geolocation, legal calculation, translation, math.

                                Authentication is JWT-based (login at /api/auth/login).
                                """)
                        .contact(new Contact()
                                .name("Wei Wei")
                                .email("vvlovqq@gmail.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Dev"),
                        new Server().url("https://agent.jimmyweidev.com").description("Production")
                ));
    }
}
