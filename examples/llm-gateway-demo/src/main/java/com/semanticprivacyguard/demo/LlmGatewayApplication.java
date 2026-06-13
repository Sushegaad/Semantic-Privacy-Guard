package com.semanticprivacyguard.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SPG LLM Gateway Demo — Spring Boot entry point.
 *
 * <p>Start with:</p>
 * <pre>
 *   mvn spring-boot:run
 * </pre>
 *
 * <p>Then try the gateway endpoint:</p>
 * <pre>
 *   curl -s -X POST http://localhost:8080/api/chat \
 *     -H "Content-Type: application/json" \
 *     -d '{"prompt":"My name is Alice and my email is alice@corp.com. Summarise my profile."}'
 * </pre>
 */
@SpringBootApplication
public class LlmGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(LlmGatewayApplication.class, args);
    }
}
