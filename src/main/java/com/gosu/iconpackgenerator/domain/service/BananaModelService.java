package com.gosu.iconpackgenerator.domain.service;

import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import ai.fal.client.queue.QueueStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.iconpackgenerator.exception.FalAiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class BananaModelService implements AIModelService {
    
    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    
    private static final String BANANA_TEXT_TO_IMAGE_ENDPOINT = "fal-ai/nano-banana";
    private static final String BANANA_IMAGE_TO_IMAGE_ENDPOINT = "fal-ai/nano-banana/edit";
    
    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        return generateImage(prompt, null);
    }
    
    /**
     * Generate image with optional seed for reproducible results
     * Note: Banana model doesn't appear to support seeds based on the schema,
     * but we maintain the interface for consistency
     */
    public CompletableFuture<byte[]> generateImage(String prompt, Long seed) {
        log.info("Generating image with Banana model for prompt: {} (seed: {})", prompt, seed);
        
        return generateBananaImageAsync(prompt, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with Banana model", error);
                    } else {
                        log.info("Successfully generated image with Banana model, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateBananaImageAsync(String prompt, Long seed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating Banana image with endpoint: {} (seed: {})", BANANA_TEXT_TO_IMAGE_ENDPOINT, seed);
                
                // Apply Banana-specific styling to the prompt with explicit constraints
                String bananaPrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders";
                
                Map<String, Object> input = createBananaTextToImageInputMap(bananaPrompt, seed);
                log.info("Making Banana text-to-image API call with input keys: {} (seed: {})", input.keySet(), seed);
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(BANANA_TEXT_TO_IMAGE_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("Banana generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from Banana API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted Banana result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (Exception e) {
                log.error("Error calling Banana API", e);
                throw new FalAiException("Failed to generate image with Banana model: " + e.getMessage(), e);
            }
        });
    }
    
    private Map<String, Object> createBananaTextToImageInputMap(String prompt, Long seed) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("num_images", 1); // Always generate 1 image
        input.put("output_format", "png"); // Use PNG for better quality with transparency
        
        // Note: Banana model doesn't seem to support seeds based on the schema,
        // but we log it for consistency
        if (seed != null) {
            log.debug("Seed provided ({}) but not supported by Banana model", seed);
        }
        
        log.debug("Banana text-to-image input parameters: {}", input);
        return input;
    }
    
    /**
     * Generate image using image-to-image functionality.
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData) {
        return generateImageToImage(prompt, sourceImageData, null);
    }
    
    /**
     * Generate image using image-to-image functionality with optional seed.
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData, Long seed) {
        log.info("Generating image-to-image with Banana model for prompt: {} (seed: {})", prompt, seed);
        
        return generateBananaImageToImageAsync(prompt, sourceImageData, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image-to-image with Banana model", error);
                    } else {
                        log.info("Successfully generated image-to-image with Banana model, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateBananaImageToImageAsync(String prompt, byte[] sourceImageData, Long seed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating Banana image-to-image with endpoint: {} (seed: {})", BANANA_IMAGE_TO_IMAGE_ENDPOINT, seed);
                
                // Convert image data to data URL for image_urls parameter
                String imageDataUrl = convertToDataUrl(sourceImageData);
                
                // Apply Banana-specific styling to the prompt
                String bananaPrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders";
                
                Map<String, Object> input = createBananaImageToImageInputMap(bananaPrompt, imageDataUrl, seed);
                log.info("Making Banana image-to-image API call with input keys: {} (seed: {})", input.keySet(), seed);
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(BANANA_IMAGE_TO_IMAGE_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("Banana image-to-image generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from Banana image-to-image API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted Banana image-to-image result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (Exception e) {
                log.error("Error calling Banana image-to-image API", e);
                throw new FalAiException("Failed to generate image-to-image with Banana model: " + e.getMessage(), e);
            }
        });
    }
    
    private Map<String, Object> createBananaImageToImageInputMap(String prompt, String imageDataUrl, Long seed) {
        Map<String, Object> input = new HashMap<>();
        input.put("image_urls", List.of(imageDataUrl)); // Banana uses image_urls list for image-to-image
        input.put("prompt", prompt);
        input.put("num_images", 1); // Always generate 1 image
        input.put("output_format", "png"); // Use PNG for better quality with transparency
        
        // Note: Banana model doesn't seem to support seeds based on the schema,
        // but we log it for consistency
        if (seed != null) {
            log.debug("Seed provided ({}) but not supported by Banana model", seed);
        }
        
        log.debug("Banana image-to-image input parameters: {}", input);
        return input;
    }
    
    private byte[] extractImageFromResult(JsonNode result) {
        try {
            // Banana returns images in the 'images' array according to the schema
            JsonNode imagesNode = result.path("images");
            if (imagesNode.isArray() && imagesNode.size() > 0) {
                JsonNode firstImage = imagesNode.get(0);
                String imageUrl = firstImage.path("url").asText();
                
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from Banana URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
                
                // Check if there's direct base64 data (alternative format)
                String base64Data = firstImage.path("base64").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in Banana response");
                    return Base64.getDecoder().decode(base64Data);
                }
                
                // Check for data URL format
                String dataUrl = firstImage.path("data").asText();
                if (dataUrl.startsWith("data:image/")) {
                    log.debug("Found data URL in Banana response");
                    String base64Part = dataUrl.substring(dataUrl.indexOf(",") + 1);
                    return Base64.getDecoder().decode(base64Part);
                }
            }
            
            // Log the description if available
            JsonNode descriptionNode = result.path("description");
            if (!descriptionNode.isMissingNode()) {
                log.debug("Banana response description: {}", descriptionNode.asText());
            }
            
            log.error("Could not extract image data from Banana result: {}", result);
            throw new FalAiException("Invalid response format from Banana model - no image URL or data found");
            
        } catch (Exception e) {
            log.error("Error extracting image from Banana response", e);
            throw new FalAiException("Failed to extract image from Banana API response: " + e.getMessage(), e);
        }
    }
    
    private byte[] downloadImageFromUrl(String imageUrl) {
        try {
            log.debug("Downloading image from URL: {}", imageUrl);
            URL url = URI.create(imageUrl).toURL();
            
            try (InputStream inputStream = url.openStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                byte[] imageData = outputStream.toByteArray();
                log.info("Successfully downloaded image from Banana: {} bytes", imageData.length);
                return imageData;
                
            }
        } catch (IOException e) {
            log.error("Failed to download image from Banana URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download image from Banana URL: " + imageUrl, e);
        }
    }
    
    private String convertToDataUrl(byte[] imageData) {
        try {
            String base64Data = Base64.getEncoder().encodeToString(imageData);
            return "data:image/png;base64," + base64Data;
        } catch (Exception e) {
            log.error("Error converting image to data URL", e);
            throw new FalAiException("Failed to convert image to data URL", e);
        }
    }
    
    @Override
    public String getModelName() {
        return "Banana Model (via fal.ai)";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Banana uses the same fal.ai infrastructure, so we can use basic checks
            return falClient != null;
        } catch (Exception e) {
            log.warn("Banana service is not available: {}", e.getMessage());
            return false;
        }
    }
}
