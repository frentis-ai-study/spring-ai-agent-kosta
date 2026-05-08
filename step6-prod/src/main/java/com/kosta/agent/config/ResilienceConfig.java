package com.kosta.agent.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * step6: OpenAI 호출에 대한 Retry + CircuitBreaker.
 * AgentController/Advisor에서 Decorators로 감싸 사용한다.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .build();
        return CircuitBreakerRegistry.of(cfg);
    }

    @Bean
    public CircuitBreaker openAiCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("openai");
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig cfg = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(java.io.IOException.class, RuntimeException.class)
                .build();
        return RetryRegistry.of(cfg);
    }

    @Bean
    public Retry openAiRetry(RetryRegistry registry) {
        return registry.retry("openai");
    }
}
