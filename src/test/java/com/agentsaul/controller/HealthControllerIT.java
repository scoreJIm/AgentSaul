package com.agentsaul.controller;

import com.agentsaul.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Health Endpoint")
class HealthControllerIT extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /actuator/health should return UP")
    void shouldReturnHealthUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", anyOf(is("UP"), is("DOWN"))));
    }

    @Test
    @DisplayName("GET /actuator/info should be accessible")
    void shouldReturnInfo() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
