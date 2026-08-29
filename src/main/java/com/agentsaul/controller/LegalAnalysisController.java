package com.agentsaul.controller;

import com.agentsaul.annotation.RateLimit;
import com.agentsaul.service.LegalAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes Spring AI structured output as an HTTP endpoint.
 */
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "Structured Output", description = "Spring AI structured output — LLM JSON mapped to a Java record")
public class LegalAnalysisController {

    private final LegalAnalysisService analysisService;

    public LegalAnalysisController(LegalAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/legal")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(limit = 10, windowSeconds = 60, scope = RateLimit.Scope.USER)
    @Operation(summary = "Analyze a legal query into a structured case analysis")
    public LegalAnalysisService.CaseAnalysis analyze(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        return analysisService.analyze(message);
    }
}
