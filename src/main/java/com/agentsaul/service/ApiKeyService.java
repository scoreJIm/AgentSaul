package com.agentsaul.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Simple API key management for MVP.
 * Accepts a single configured API key from application.yml.
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final String configuredApiKey;

    public ApiKeyService(@Value("${agentsaul.api-key:}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            log.warn("No API key configured. API key login will be disabled.");
        } else {
            log.info("API key authentication enabled");
        }
    }

    public boolean isValidApiKey(String apiKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return false;
        }
        return configuredApiKey.equals(apiKey);
    }

    public String getUserIdForApiKey() {
        return "apikey-user";
    }

    public String getRoleForApiKey() {
        return "USER";
    }
}
