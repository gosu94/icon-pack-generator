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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImagenModelService implements AIModelService {
    
    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    private final GptModelService gptModelService; // For image-to-image fallback
    
    private static final String IMAGEN_ENDPOINT = "fal-ai/imagen4/preview";
    
    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        return generateImage(prompt, null);
    }
    
    /**
     * Generate image with optional seed for reproducible results
     */
    public CompletableFuture<byte[]> generateImage(String prompt, Long seed) {
        log.info("Generating image with Imagen 4 for prompt: {} (seed: {})", prompt, seed);
        
        return generateImagenImageAsync(prompt, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with Imagen", error);
                    } else {
                        log.info("Successfully generated image with Imagen, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateImagenImageAsync(String prompt, Long seed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating Imagen image with endpoint: {} (seed: {})", IMAGEN_ENDPOINT, seed);
                
                // Apply Imagen-specific styling to the prompt with explicit constraints
                String imagenPrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders";
                
                Map<String, Object> input = createImagenInputMap(imagenPrompt, seed);
                log.info("Making Imagen API call with input keys: {} (seed: {})", input.keySet(), seed);
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(IMAGEN_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("Imagen generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from Imagen API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted Imagen result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (Exception e) {
                log.error("Error calling Imagen API", e);
                throw new FalAiException("Failed to generate image with Imagen 4: " + e.getMessage(), e);
            }
        });
    }
    
    private Map<String, Object> createImagenInputMap(String prompt, Long seed) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("negative_prompt", ""); // Empty negative prompt by default
        input.put("aspect_ratio", "1:1"); // Square aspect ratio for icons
        input.put("num_images", 1); // Generate 1 image
        
        // Add seed if provided, otherwise let the API use its default random seed
        if (seed != null) {
            input.put("seed", seed);
        }
        
        log.debug("Imagen input parameters: {}", input);
        return input;
    }
    
    private byte[] extractImageFromResult(JsonNode result) {
        try {
            // Imagen likely returns images in the 'images' array (following fal.ai pattern)
            JsonNode imagesNode = result.path("images");
            if (imagesNode.isArray() && imagesNode.size() > 0) {
                JsonNode firstImage = imagesNode.get(0);
                String imageUrl = firstImage.path("url").asText();
                
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from Imagen URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
                
                // Check if there's direct base64 data (alternative format)
                String base64Data = firstImage.path("base64").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in Imagen response");
                    return Base64.getDecoder().decode(base64Data);
                }
                
                // Check for data URL format
                String dataUrl = firstImage.path("data").asText();
                if (dataUrl.startsWith("data:image/")) {
                    log.debug("Found data URL in Imagen response");
                    String base64Part = dataUrl.substring(dataUrl.indexOf(",") + 1);
                    return Base64.getDecoder().decode(base64Part);
                }
            }
            
            // Check if the result has a direct image field
            JsonNode imageNode = result.path("image");
            if (imageNode != null && !imageNode.isMissingNode()) {
                String imageUrl = imageNode.path("url").asText();
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from Imagen image URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
            }
            
            log.error("Could not extract image data from Imagen result: {}", result);
            throw new FalAiException("Invalid response format from Imagen 4 - no image URL or data found");
            
        } catch (Exception e) {
            log.error("Error extracting image from Imagen response", e);
            throw new FalAiException("Failed to extract image from Imagen 4 API response: " + e.getMessage(), e);
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
                log.info("Successfully downloaded image from Imagen: {} bytes", imageData.length);
                return imageData;
                
            }
        } catch (IOException e) {
            log.error("Failed to download image from Imagen URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download image from Imagen 4 URL: " + imageUrl, e);
        }
    }
    
    /**
     * Generate image using image-to-image functionality.
     * Since Imagen doesn't support image-to-image, we delegate to GptModelService.
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData) {
        return generateImageToImage(prompt, sourceImageData, null);
    }
    
    /**
     * Generate image using image-to-image functionality with optional seed.
     * Since Imagen doesn't support image-to-image, we delegate to GptModelService.
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData, Long seed) {
        log.info("Imagen doesn't support image-to-image, delegating to GPT for prompt: {} (seed: {})", 
                prompt.substring(0, Math.min(100, prompt.length())), seed);
        
        // Apply Imagen-style prompt formatting but use GPT for image-to-image
        String imagenStylePrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders";
        
        return gptModelService.generateImageToImage(imagenStylePrompt, sourceImageData, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error in GPT image-to-image fallback for Imagen", error);
                    } else {
                        log.info("Successfully generated image-to-image with GPT fallback for Imagen, size: {} bytes", bytes.length);
                    }
                });
    }
    
    @Override
    public String getModelName() {
        return "Imagen 4 (via fal.ai)";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Imagen uses the same fal.ai infrastructure, so check basic connectivity
            return gptModelService.isAvailable();
        } catch (Exception e) {
            log.warn("Imagen service is not available: {}", e.getMessage());
            return false;
        }
    }
}
