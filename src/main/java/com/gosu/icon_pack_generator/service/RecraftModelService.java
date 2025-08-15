package com.gosu.icon_pack_generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class RecraftModelService implements AIModelService {
    
    private final FalAiModelService falAiModelService;
    
    public RecraftModelService(FalAiModelService falAiModelService) {
        this.falAiModelService = falAiModelService;
    }
    
    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        log.info("Generating image with Recraft V3 for prompt: {}", prompt.substring(0, Math.min(100, prompt.length())));
        
        // For now, delegate to FalAI service with a modified prompt to indicate Recraft style
        String recraftPrompt = "digital illustration style: " + prompt;
        
        return falAiModelService.generateImage(recraftPrompt)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with Recraft", error);
                    } else {
                        log.info("Successfully generated image with Recraft, size: {} bytes", bytes.length);
                    }
                });
    }
    
    @Override
    public String getModelName() {
        return "Recraft V3 (via Flux-Pro)";
    }
    
    @Override
    public boolean isAvailable() {
        // Recraft is available if FalAI is available
        return falAiModelService.isAvailable();
    }
    
    /**
     * Test the API connection by using the FalAI service
     */
    public CompletableFuture<Boolean> testConnection() {
        return falAiModelService.testConnection();
    }
}
