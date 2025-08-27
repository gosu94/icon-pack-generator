package com.gosu.iconpackgenerator.config;

import ai.fal.client.FalClient;
import com.gosu.iconpackgenerator.domain.model.AIModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class FalAiConfig {

    private final AIModelConfig aiModelConfig;

    @Bean
    public FalClient falClient() {
        try {
            // Set the API key as environment variable for the client
            if (aiModelConfig.getApiKey() != null && !aiModelConfig.getApiKey().equals("your-fal-api-key-here")) {
                System.setProperty("FAL_KEY", aiModelConfig.getApiKey());
                log.info("Configured fal.ai client with API key {}", aiModelConfig.getApiKey());
                return FalClient.withEnvCredentials();
            } else {
                log.warn("No valid fal.ai API key provided. Set FAL_API_KEY environment variable or update application.properties");
                return FalClient.withEnvCredentials();
            }
        } catch (Exception e) {
            log.error("Failed to initialize fal.ai client", e);
            throw new RuntimeException("Could not initialize fal.ai client", e);
        }
    }
}
