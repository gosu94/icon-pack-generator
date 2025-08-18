package com.gosu.icon_pack_generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.icon_pack_generator.exception.FalAiException;
import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import ai.fal.client.queue.QueueStatus;
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
public class PhotonModelService implements AIModelService {
    
    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    private final FluxModelService fluxModelService; // For image-to-image fallback
    
    private static final String PHOTON_ENDPOINT = "fal-ai/luma-photon";
    
    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        log.info("Generating image with Luma Photon for prompt: {}", prompt);
        
        return generatePhotonImageAsync(prompt)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with Photon", error);
                    } else {
                        log.info("Successfully generated image with Photon, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generatePhotonImageAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating Photon image with endpoint: {}", PHOTON_ENDPOINT);
                
                // Apply Photon-specific styling to the prompt with explicit constraints
                String photonPrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders";
                
                Map<String, Object> input = createPhotonInputMap(photonPrompt);
                log.info("Making Photon API call with input keys: {}", input.keySet());
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(PHOTON_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("Photon generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from Photon API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted Photon result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (Exception e) {
                log.error("Error calling Photon API", e);
                throw new FalAiException("Failed to generate image with Luma Photon: " + e.getMessage(), e);
            }
        });
    }
    
    private Map<String, Object> createPhotonInputMap(String prompt) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        
        log.debug("Photon input parameters: {}", input);
        return input;
    }
    
    private byte[] extractImageFromResult(JsonNode result) {
        try {
            // Photon likely returns images in the 'images' array (following fal.ai pattern)
            JsonNode imagesNode = result.path("images");
            if (imagesNode.isArray() && imagesNode.size() > 0) {
                JsonNode firstImage = imagesNode.get(0);
                String imageUrl = firstImage.path("url").asText();
                
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from Photon URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
                
                // Check if there's direct base64 data (alternative format)
                String base64Data = firstImage.path("base64").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in Photon response");
                    return Base64.getDecoder().decode(base64Data);
                }
                
                // Check for data URL format
                String dataUrl = firstImage.path("data").asText();
                if (dataUrl.startsWith("data:image/")) {
                    log.debug("Found data URL in Photon response");
                    String base64Part = dataUrl.substring(dataUrl.indexOf(",") + 1);
                    return Base64.getDecoder().decode(base64Part);
                }
            }
            
            // Check if the result has a direct image field
            JsonNode imageNode = result.path("image");
            if (imageNode != null && !imageNode.isMissingNode()) {
                String imageUrl = imageNode.path("url").asText();
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from Photon image URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
            }
            
            log.error("Could not extract image data from Photon result: {}", result);
            throw new FalAiException("Invalid response format from Luma Photon - no image URL or data found");
            
        } catch (Exception e) {
            log.error("Error extracting image from Photon response", e);
            throw new FalAiException("Failed to extract image from Photon API response: " + e.getMessage(), e);
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
                log.info("Successfully downloaded image from Photon: {} bytes", imageData.length);
                return imageData;
                
            }
        } catch (IOException e) {
            log.error("Failed to download image from Photon URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download image from Luma Photon URL: " + imageUrl, e);
        }
    }
    
    /**
     * Generate image using image-to-image functionality.
     * Since Photon doesn't support image-to-image, we delegate to FluxModelService.
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData) {
        log.info("Photon doesn't support image-to-image, delegating to Flux for prompt: {}", 
                prompt.substring(0, Math.min(100, prompt.length())));
        
        // Apply Photon-style prompt formatting but use Flux for image-to-image
        String photonStylePrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders";
        
        return fluxModelService.generateImageToImage(photonStylePrompt, sourceImageData)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error in Flux image-to-image fallback for Photon", error);
                    } else {
                        log.info("Successfully generated image-to-image with Flux fallback for Photon, size: {} bytes", bytes.length);
                    }
                });
    }
    
    @Override
    public String getModelName() {
        return "Luma Photon";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Photon uses the same fal.ai infrastructure, so check basic connectivity
            return fluxModelService.isAvailable();
        } catch (Exception e) {
            log.warn("Photon service is not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Test the API connection with a simple request
     */
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Testing Photon connection with endpoint: {}", PHOTON_ENDPOINT);
                
                // Create a minimal test input
                Map<String, Object> testInput = new HashMap<>();
                testInput.put("prompt", "test icon");
                
                log.debug("Photon test input: {}", testInput);
                
                try {
                    // Use the fal.ai client to test connection
                    Output<JsonObject> output = falClient.subscribe(PHOTON_ENDPOINT,
                        SubscribeOptions.<JsonObject>builder()
                            .input(testInput)
                            .logs(true)
                            .resultType(JsonObject.class)
                            .build()
                    );
                    JsonObject result = output.getData();
                    log.debug("Photon API call completed, result: {}", result);
                    log.info("Photon connection test successful, result: {}", result);
                    return true;
                } catch (Exception apiException) {
                    log.error("Photon API call failed", apiException);
                    throw apiException;
                }
                
            } catch (Exception e) {
                log.error("Photon connection test failed: {}", e.getMessage(), e);
                return false;
            }
        });
    }
}
