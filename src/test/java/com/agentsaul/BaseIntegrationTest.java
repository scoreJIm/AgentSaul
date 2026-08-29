package com.agentsaul;

import com.agentsaul.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests.
 * <p>
 * Uses Testcontainers JDBC URL integration ({@code jdbc:tc:postgresql:16:///})
 * to spin up a PostgreSQL 16 container automatically at datasource initialization.
 * No {@code @Container} or {@code @DynamicPropertySource} annotations needed
 * -- the datasource URL itself triggers container startup via Testcontainers'
 * JDBC driver.
 * <p>
 * Requires Docker to be running when executing tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    /**
     * Generate a valid Bearer token for test authentication.
     */
    protected String createAuthToken(String userId, String role) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(userId, role);
    }

    /**
     * Generate a valid refresh token for test authentication.
     */
    protected String createRefreshToken(String userId) {
        return jwtTokenProvider.generateRefreshToken(userId);
    }
}
