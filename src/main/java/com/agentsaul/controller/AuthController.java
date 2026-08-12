package com.agentsaul.controller;

import com.agentsaul.dto.LoginRequest;
import com.agentsaul.dto.RefreshTokenRequest;
import com.agentsaul.dto.TokenResponse;
import com.agentsaul.security.JwtTokenProvider;
import com.agentsaul.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication endpoints — login, refresh, and API key validation")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final ApiKeyService apiKeyService;

    public AuthController(JwtTokenProvider jwtTokenProvider, ApiKeyService apiKeyService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with username/password or API key. Returns JWT access + refresh tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // API key authentication
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            if (apiKeyService.isValidApiKey(request.getApiKey())) {
                String userId = apiKeyService.getUserIdForApiKey();
                String role = apiKeyService.getRoleForApiKey();
                String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
                String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
                log.info("API key login: userId={}", userId);
                return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken, 1800));
            }
            log.warn("Invalid API key attempt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "errorCode", "INVALID_API_KEY",
                            "message", "Invalid API key"));
        }

        // Username/password authentication (MVP: hardcoded admin user)
        if (request.getUsername() != null && request.getPassword() != null) {
            if ("admin".equals(request.getUsername()) && "agentsaul123".equals(request.getPassword())) {
                String accessToken = jwtTokenProvider.generateAccessToken("1", "ADMIN");
                String refreshToken = jwtTokenProvider.generateRefreshToken("1");
                log.info("Admin login successful");
                return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken, 1800));
            }
            log.warn("Failed login attempt for username={}", request.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "errorCode", "INVALID_CREDENTIALS",
                            "message", "Invalid username or password"));
        }

        log.warn("Login request without credentials");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "errorCode", "MISSING_CREDENTIALS",
                        "message", "Provide username/password or apiKey"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Exchange a valid refresh token for a new access + refresh token pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "errorCode", "MISSING_TOKEN",
                            "message", "Refresh token is required"));
        }

        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "errorCode", "INVALID_TOKEN",
                            "message", "Invalid or expired refresh token"));
        }

        String userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        String role = jwtTokenProvider.getRoleFromToken(request.getRefreshToken());

        String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        log.info("Token refreshed for userId={}", userId);
        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken, 1800));
    }
}
