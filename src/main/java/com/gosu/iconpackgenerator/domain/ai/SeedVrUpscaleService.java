package com.gosu.iconpackgenerator.domain.ai;

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
public class SeedVrUpscaleService {
    
    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    
    private static final String SEEDVR_UPSCALE_ENDPOINT = "fal-ai/seedvr/upscale/image";
    private static final float DEFAULT_UPSCALE_FACTOR = 2.0f;
    
    /**
     * Upscale an image from byte array data using the SeedVR upscale model.
     * 
     * @param imageData The image data as byte array
     * @return CompletableFuture with the upscaled image data
     */
    public CompletableFuture<byte[]> upscaleImage(byte[] imageData) {
        return upscaleImage(imageData, DEFAULT_UPSCALE_FACTOR, null);
    }
    
    /**
     * Upscale an image from byte array with specific upscale factor.
     * 
     * @param imageData The image data as byte array
     * @param upscaleFactor The upscale factor (multiplies dimensions by this factor)
     * @return CompletableFuture with the upscaled image data
     */
    public CompletableFuture<byte[]> upscaleImage(byte[] imageData, float upscaleFactor) {
        return upscaleImage(imageData, upscaleFactor, null);
    }
    
    /**
     * Upscale an image from byte array with optional seed for reproducible results.
     * 
     * @param imageData The image data as byte array
     * @param upscaleFactor The upscale factor (multiplies dimensions by this factor)
     * @param seed Optional seed for reproducibility
     * @return CompletableFuture with the upscaled image data
     */
    public CompletableFuture<byte[]> upscaleImage(byte[] imageData, float upscaleFactor, Integer seed) {
        String dataUri = convertToDataUri(imageData);
        return upscaleImage(dataUri, upscaleFactor, seed);
    }
    
    /**
     * Upscale an image using the SeedVR upscale model.
     * 
     * @param imageUrl The URL of the image to upscale
     * @return CompletableFuture with the upscaled image data
     */
    public CompletableFuture<byte[]> upscaleImage(String imageUrl) {
        return upscaleImage(imageUrl, DEFAULT_UPSCALE_FACTOR, null);
    }
    
    /**
     * Upscale an image with a specific upscale factor.
     * 
     * @param imageUrl The URL of the image to upscale
     * @param upscaleFactor The upscale factor (multiplies dimensions by this factor)
     * @return CompletableFuture with the upscaled image data
     */
    public CompletableFuture<byte[]> upscaleImage(String imageUrl, float upscaleFactor) {
        return upscaleImage(imageUrl, upscaleFactor, null);
    }
    
    /**
     * Upscale an image with optional seed for reproducible results.
     * 
     * @param imageUrl The URL of the image to upscale
     * @param upscaleFactor The upscale factor (multiplies dimensions by this factor)
     * @param seed Optional seed for reproducibility
     * @return CompletableFuture with the upscaled image data
     */
    public CompletableFuture<byte[]> upscaleImage(String imageUrl, float upscaleFactor, Integer seed) {
        log.info("Upscaling image with SeedVR with factor: {}", upscaleFactor);
        
        return upscaleImageAsync(imageUrl, upscaleFactor, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error upscaling image with SeedVR", error);
                    } else {
                        log.info("Successfully upscaled image with SeedVR, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> upscaleImageAsync(String imageUrl, float upscaleFactor, Integer seed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Upscaling image with endpoint: {}", SEEDVR_UPSCALE_ENDPOINT);
                
                Map<String, Object> input = createUpscaleInputMap(imageUrl, upscaleFactor, seed);
                log.info("Making SeedVR upscale API call with input keys: {}", input.keySet());
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(SEEDVR_UPSCALE_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("SeedVR upscale progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from SeedVR upscale API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted SeedVR upscale result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (ai.fal.client.exception.FalException e) {
                log.error("SeedVR upscale API error: {}", e.getMessage());
                String userFriendlyMessage = sanitizeSeedVrError(e);
                throw new FalAiException(userFriendlyMessage, e);
            } catch (Exception e) {
                log.error("Error calling SeedVR upscale API", e);
                throw new FalAiException("Failed to upscale image with SeedVR. Please try again or use a different image.", e);
            }
        });
    }
    
    private Map<String, Object> createUpscaleInputMap(String imageUrl, float upscaleFactor, Integer seed) {
        Map<String, Object> input = new HashMap<>();
        input.put("image_url", imageUrl);
        input.put("upscale_factor", upscaleFactor);
        
        // Add seed if provided
        if (seed != null) {
            input.put("seed", seed);
        }
        
        log.debug("SeedVR upscale input parameters: {}", input);
        return input;
    }
    
    private byte[] extractImageFromResult(JsonNode result) {
        try {
            // SeedVR returns the image in the 'image' field
            JsonNode imageNode = result.path("image");
            
            if (imageNode != null && !imageNode.isMissingNode()) {
                // Get the URL from the image object
                String imageUrl = imageNode.path("url").asText();
                
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading upscaled image from SeedVR URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
                
                // Check for content_type and other metadata
                String contentType = imageNode.path("content_type").asText();
                log.debug("Image content type: {}", contentType);
            }
            
            // Also log the seed used if available
            JsonNode seedNode = result.path("seed");
            if (!seedNode.isMissingNode()) {
                log.info("SeedVR upscale used seed: {}", seedNode.asInt());
            }
            
            log.error("Could not extract image data from SeedVR result: {}", result);
            throw new FalAiException("Invalid response format from SeedVR - no image URL found");
            
        } catch (Exception e) {
            log.error("Error extracting image from SeedVR response", e);
            throw new FalAiException("Failed to extract image from SeedVR API response: " + e.getMessage(), e);
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
                log.info("Successfully downloaded upscaled image from SeedVR: {} bytes", imageData.length);
                return imageData;
                
            }
        } catch (IOException e) {
            log.error("Failed to download image from SeedVR URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download image from SeedVR URL: " + imageUrl, e);
        }
    }
    
    /**
     * Sanitize SeedVR/Fal.ai API errors into user-friendly messages
     */
    private String sanitizeSeedVrError(ai.fal.client.exception.FalException e) {
        String errorMessage = e.getMessage();
        
        // Extract error code if available
        if (errorMessage.contains("422")) {
            return "Unable to process the upscale request. The image URL may be invalid or the image format may not be supported. Please try:\n" +
                   "- Using a different image URL\n" +
                   "- Ensuring the image is accessible\n" +
                   "- Using a standard image format (PNG, JPEG)";
        } else if (errorMessage.contains("400")) {
            return "Invalid request. Please check your image URL and upscale factor.";
        } else if (errorMessage.contains("401") || errorMessage.contains("403")) {
            return "Authentication error with the AI service. Please contact support.";
        } else if (errorMessage.contains("429")) {
            return "Too many requests. Please wait a moment and try again.";
        } else if (errorMessage.contains("500") || errorMessage.contains("503")) {
            return "The AI service is temporarily unavailable. Please try again in a few moments.";
        }
        
        // Generic fallback
        return "Failed to upscale. Please try again or use a different image.";
    }
    
    public String getModelName() {
        return "SeedVR Upscale (via fal.ai)";
    }
    
    public boolean isAvailable() {
        try {
            // SeedVR uses fal.ai infrastructure, check if client is configured
            return falClient != null;
        } catch (Exception e) {
            log.warn("SeedVR upscale service is not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Convert byte array image data to data URI format
     */
    private String convertToDataUri(byte[] imageData) {
        try {
            String base64Data = Base64.getEncoder().encodeToString(imageData);
            // Detect image format (PNG or JPEG) from the image header
            String mimeType = detectImageMimeType(imageData);
            return "data:" + mimeType + ";base64," + base64Data;
        } catch (Exception e) {
            log.error("Error converting image to data URI", e);
            throw new FalAiException("Failed to convert image to data URI: " + e.getMessage(), e);
        }
    }
    
    /**
     * Detect image MIME type from byte array header
     */
    private String detectImageMimeType(byte[] imageData) {
        if (imageData.length < 4) {
            return "image/png"; // Default to PNG
        }
        
        // Check for PNG signature
        if (imageData[0] == (byte) 0x89 && imageData[1] == 'P' && 
            imageData[2] == 'N' && imageData[3] == 'G') {
            return "image/png";
        }
        
        // Check for JPEG signature
        if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        
        // Check for WebP signature
        if (imageData[0] == 'R' && imageData[1] == 'I' && 
            imageData[2] == 'F' && imageData[3] == 'F') {
            return "image/webp";
        }
        
        // Default to PNG
        return "image/png";
    }
}

