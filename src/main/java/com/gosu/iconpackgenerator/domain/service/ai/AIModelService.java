package com.gosu.iconpackgenerator.domain.service.ai;

import java.util.concurrent.CompletableFuture;

public interface AIModelService {
    
    /**
     * Generate an image based on the provided prompt
     * @param prompt The text prompt for image generation
     * @return CompletableFuture containing the generated image as byte array
     */
    CompletableFuture<byte[]> generateImage(String prompt);
    
    /**
     * Get the model name
     * @return The name of the AI model
     */
    String getModelName();
    
    /**
     * Check if the service is available
     * @return true if the service is available
     */
    boolean isAvailable();
}
