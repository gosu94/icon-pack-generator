package com.gosu.iconpackgenerator.config;

import ai.fal.client.FalClient;
import com.gosu.iconpackgenerator.domain.icons.model.AIModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

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
                log.info("Configured fal.ai client with API key");
                
                // Create FalClient with environment credentials
                FalClient client = FalClient.withEnvCredentials();
                
                // Configure the underlying OkHttpClient to properly manage connections
                configureOkHttpClient(client);
                
                return client;
            } else {
                log.warn("No valid fal.ai API key provided. Set FAL_API_KEY environment variable or update application.properties");
                FalClient client = FalClient.withEnvCredentials();
                configureOkHttpClient(client);
                return client;
            }
        } catch (Exception e) {
            log.error("Failed to initialize fal.ai client", e);
            throw new RuntimeException("Could not initialize fal.ai client", e);
        }
    }
    
    /**
     * Configure the OkHttpClient used by FalClient to properly manage connections
     * and prevent connection leaks.
     */
    private void configureOkHttpClient(FalClient client) {
        try {
            // Access the internal httpClient field via reflection
            Field httpClientField = client.getClass().getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            OkHttpClient existingClient = (OkHttpClient) httpClientField.get(client);
            
            // Create a new OkHttpClient with proper connection management
            // Increased timeouts to handle large image upscaling (e.g., UI mockups in 16:9 aspect ratio)
            OkHttpClient newClient = existingClient.newBuilder()
                    .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                    .readTimeout(240, TimeUnit.SECONDS)  // Increased to 5 minutes for large image upscaling
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    // Enable connection eviction
                    .retryOnConnectionFailure(true)
                    .build();
            
            // Replace the httpClient in FalClient
            httpClientField.set(client, newClient);
            
            log.info("Successfully configured OkHttpClient with connection pool management");
        } catch (NoSuchFieldException e) {
            log.warn("Could not find httpClient field in FalClient - the library structure may have changed. " +
                    "Connection pool configuration skipped.");
        } catch (Exception e) {
            log.warn("Could not configure OkHttpClient for FalClient: {}. Using default configuration.", 
                    e.getMessage());
        }
    }
}
