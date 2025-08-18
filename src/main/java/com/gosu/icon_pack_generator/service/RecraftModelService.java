package com.gosu.icon_pack_generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class RecraftModelService implements AIModelService {
    
    private final FluxModelService fluxModelService;
    
    public RecraftModelService(FluxModelService fluxModelService) {
        this.fluxModelService = fluxModelService;
    }
    
    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        log.info("Generating image with Recraft V3 for prompt: {}", prompt);
        
        return generateRecraftImageAsync(prompt)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with Recraft", error);
                    } else {
                        log.info("Successfully generated image with Recraft, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateRecraftImageAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> input = null;
            try {
                log.info("Generating Recraft image with endpoint: fal-ai/recraft/v3/text-to-image");
                
                        // Apply Recraft-specific styling to the prompt with explicit constraints
        String recraftPrompt = "digital illustration style: " + prompt + 
                " No text, no labels, no grid lines, no borders. Clean icon design only.";
                
                input = createRecraftTextToImageInputMap(recraftPrompt);
                log.info("Making Recraft text-to-image API call with input keys: {}", input.keySet());
                
                // Use the same falClient from FalAiModelService since both use fal.ai infrastructure
                return fluxModelService.generateImageWithCustomEndpoint("fal-ai/recraft/v3/text-to-image", input);
                
            } catch (Exception e) {
                log.error("Error calling Recraft text-to-image API, falling back to FalAI generation. " +
                         "Original parameters that failed: {}", input != null ? input : "unknown", e);
                
                // Check if it's a parameter validation error
                if (e.getMessage() != null && e.getMessage().contains("422")) {
                    log.warn("Recraft API returned 422 error - this suggests parameter incompatibility. " +
                            "Common issues: unsupported image_size, invalid style, or endpoint parameter mismatch.");
                }
                
                // Fallback to FalAI generation with modified prompt
                String recraftPrompt = "digital illustration style: " + prompt + 
                        " No text, no labels, no grid lines, no borders. Clean icon design only.";
                        
                log.info("Falling back to standard FalAI generation with prompt: {}", 
                        recraftPrompt.length() > 100 ? recraftPrompt.substring(0, 100) + "..." : recraftPrompt);
                
                return fluxModelService.generateImage(recraftPrompt).join();
            }
        });
    }

    private Map<String, Object> createRecraftTextToImageInputMap(String prompt) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("image_size", "square_hd"); // Recraft uses image_size instead of aspect_ratio
        input.put("style", "digital_illustration"); // Recraft-specific style parameter

        log.info("Recraft text-to-image requesting PNG format explicitly");
        log.debug("Recraft text-to-image input parameters: {}", input);
        return input;
    }
    
    @Override
    public String getModelName() {
        return "Recraft V3 (via Flux-Pro)";
    }
    
    @Override
    public boolean isAvailable() {
        // Recraft is available if FalAI is available
        return fluxModelService.isAvailable();
    }
    
    /**
     * Generate image using Recraft V3 image-to-image functionality
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData) {
        log.info("Generating image-to-image with Recraft V3 for prompt: {}", prompt.substring(0, Math.min(100, prompt.length())));
        
        return generateRecraftImageToImageAsync(prompt, sourceImageData)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image-to-image with Recraft", error);
                    } else {
                        log.info("Successfully generated image-to-image with Recraft, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateRecraftImageToImageAsync(String prompt, byte[] sourceImageData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating Recraft image-to-image with endpoint: fal-ai/recraft/v3/image-to-image");
                
                // Convert image data to data URL for image_url parameter
                String imageDataUrl = convertToDataUrl(sourceImageData);
                
                // Apply Recraft-specific styling to the prompt with explicit constraints
                String recraftPrompt = "digital illustration style, consistent with source image: " + prompt + 
                        " No text, no labels, no grid lines, no borders. Clean icon design only.";
                
                Map<String, Object> input = createRecraftImageToImageInputMap(recraftPrompt, imageDataUrl);
                log.info("Making Recraft image-to-image API call with input keys: {}", input.keySet());
                
                // Use the same falClient from FalAiModelService since both use fal.ai infrastructure
                return fluxModelService.generateImageWithCustomEndpoint("fal-ai/recraft/v3/image-to-image", input);
                
            } catch (Exception e) {
                log.error("Error calling Recraft image-to-image API, falling back to regular generation", e);
                // Fallback to regular generation with modified prompt
                String recraftPrompt = "digital illustration style, consistent with source image: " + prompt + 
                        " No text, no labels, no grid lines, no borders. Clean icon design only.";
                return fluxModelService.generateImage(recraftPrompt).join();
            }
        });
    }



    private Map<String, Object> createRecraftImageToImageInputMap(String prompt, String imageDataUrl) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("image_url", imageDataUrl);  // This is the key parameter for image-to-image!
        input.put("image_size", "square_hd"); // Recraft uses image_size instead of aspect_ratio
        input.put("style", "digital_illustration"); // Recraft-specific style parameter

        log.info("Recraft image-to-image requesting PNG format explicitly");
        log.debug("Recraft image-to-image input parameters: {}", input);
        return input;
    }
    
    private String convertToDataUrl(byte[] imageData) {
        try {
            String base64Data = Base64.getEncoder().encodeToString(imageData);
            return "data:image/png;base64," + base64Data;
        } catch (Exception e) {
            log.error("Error converting image to data URL", e);
            // Return null so calling code can handle gracefully
            return null;
        }
    }

}
