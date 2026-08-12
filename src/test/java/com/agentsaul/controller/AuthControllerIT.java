package com.agentsaul.controller;

import com.agentsaul.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Auth API Integration Tests")
class AuthControllerIT extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("should return tokens on successful admin login")
        void shouldLoginSuccessfully() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"admin\", \"password\": \"agentsaul123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(1800));
        }

        @Test
        @DisplayName("should return 401 on invalid password")
        void shouldRejectInvalidPassword() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"admin\", \"password\": \"wrongpassword\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("should return 401 on non-existent user")
        void shouldRejectNonExistentUser() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"nobody\", \"password\": \"whatever\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("should return 400 when no credentials provided")
        void shouldRejectMissingCredentials() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("MISSING_CREDENTIALS"));
        }

        @Test
        @DisplayName("should login with valid API key")
        void shouldLoginWithApiKey() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\": \"test-api-key-12345\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()));
        }

        @Test
        @DisplayName("should reject invalid API key")
        void shouldRejectInvalidApiKey() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\": \"invalid-key\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_API_KEY"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshToken {

        @Test
        @DisplayName("should return new tokens with valid refresh token")
        void shouldRefreshTokenSuccessfully() throws Exception {
            String refreshToken = createRefreshToken("1");

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }

        @Test
        @DisplayName("should return 401 with invalid refresh token")
        void shouldRejectInvalidRefreshToken() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"invalid-token\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("should return 400 when refresh token is missing")
        void shouldRejectMissingRefreshToken() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("MISSING_TOKEN"));
        }

        @Test
        @DisplayName("should return 400 when refresh token is blank")
        void shouldRejectBlankRefreshToken() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("MISSING_TOKEN"));
        }
    }
}
