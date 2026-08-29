package com.agentsaul.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j configuration for circuit breaker, time limiter, and retry
 * on LLM API calls (DashScope/OpenAI-compatible).
 *
 * Circuit breaker: 3 failures → open for 30s, sliding window of 10 calls.
 * Time limiter: 60s timeout for chat streaming calls.
 * Retry: 2 retries with 2s initial / 4s max exponential backoff.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker llmApiCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("llmApi");
    }

    @Bean
    public TimeLimiter llmApiTimeLimiter(TimeLimiterRegistry registry) {
        return registry.timeLimiter("llmApi");
    }

    @Bean
    public Retry llmApiRetry(RetryRegistry registry) {
        return registry.retry("llmApi");
    }
}
