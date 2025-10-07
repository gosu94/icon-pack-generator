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
     * Generate image with optional seed for reproducible results.
     * Note: Banana doesn't explicitly support seed parameter based on the API schema.
     */
    public CompletableFuture<byte[]> generateImage(String prompt, Long seed) {
        log.info("Generating image with Nano Banana for prompt: {}", prompt);
        
        return generateBananaImageAsync(prompt)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with Banana", error);
                    } else {
                        log.info("Successfully generated image with Banana, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateBananaImageAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating Banana image with endpoint: {}", BANANA_TEXT_TO_IMAGE_ENDPOINT);
                
                // Apply Banana-specific styling to the prompt with explicit constraints
                String bananaPrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders";
                
                Map<String, Object> input = createBananaTextToImageInputMap(bananaPrompt);
                log.info("Making Banana API call with input keys: {}", input.keySet());
                
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
                
            } catch (ai.fal.client.exception.FalException e) {
                log.error("Banana API error with code: {}", e.getMessage());
                String userFriendlyMessage = sanitizeBananaError(e);
                throw new FalAiException(userFriendlyMessage, e);
            } catch (Exception e) {
                log.error("Error calling Banana API", e);
                throw new FalAiException("Failed to generate image with Nano Banana. Please try again or use a different prompt.", e);
            }
        });
    }
    
    private Map<String, Object> createBananaTextToImageInputMap(String prompt) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("num_images", 1); // Generate 1 image
        input.put("output_format", "png"); // Use PNG for better quality icons
        input.put("aspect_ratio", "4:3"); // Square aspect ratio for icons
        
        log.debug("Banana text-to-image input parameters: {}", input);
        return input;
    }
    
    /**
     * Generate image using image-to-image functionality.
     * Banana natively supports image-to-image editing.
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData) {
        return generateImageToImage(prompt, sourceImageData, null);
    }
    
    /**
     * Generate image using image-to-image functionality with optional seed.
     * Banana natively supports image-to-image editing via the edit endpoint.
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData, Long seed) {
        log.info("Generating image-to-image with Banana for prompt: {}", 
                prompt.substring(0, Math.min(100, prompt.length())));
        
        return generateBananaImageToImageAsync(prompt, sourceImageData)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error in Banana image-to-image generation", error);
                    } else {
                        log.info("Successfully generated image-to-image with Banana, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateBananaImageToImageAsync(String prompt, byte[] sourceImageData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating Banana image-to-image with endpoint: {}", BANANA_IMAGE_TO_IMAGE_ENDPOINT);
                
                // Apply Banana-style prompt formatting
                String bananaStylePrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders";
                
                // Convert byte array to data URI for the API
                String dataUri = convertToDataUri(sourceImageData);
                
                Map<String, Object> input = createBananaImageToImageInputMap(bananaStylePrompt, dataUri);
                log.info("Making Banana image-to-image API call with input keys: {}", input.keySet());
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(BANANA_IMAGE_TO_IMAGE_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("Banana image-to-image progress: {}", 
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
                
            } catch (ai.fal.client.exception.FalException e) {
                log.error("Banana image-to-image API error with code: {}", e.getMessage());
                String userFriendlyMessage = sanitizeBananaError(e);
                throw new FalAiException(userFriendlyMessage, e);
            } catch (Exception e) {
                log.error("Error calling Banana image-to-image API", e);
                throw new FalAiException("Failed to generate image with Nano Banana. The reference image may be invalid. Please try a different image.", e);
            }
        });
    }
    
    private Map<String, Object> createBananaImageToImageInputMap(String prompt, String imageDataUri) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("image_urls", List.of(imageDataUri)); // Banana expects a list of image URLs/data URIs
        input.put("num_images", 1); // Generate 1 image
        input.put("output_format", "png"); // Use PNG for better quality icons
        input.put("aspect_ratio", "4:3"); // Square aspect ratio for icons
        
        log.debug("Banana image-to-image input parameters: {}", input);
        return input;
    }
    
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
    
    private byte[] extractImageFromResult(JsonNode result) {
        try {
            // Banana returns images in the 'images' array
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
            
            // Check if the result has a direct image field
            JsonNode imageNode = result.path("image");
            if (imageNode != null && !imageNode.isMissingNode()) {
                String imageUrl = imageNode.path("url").asText();
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from Banana image URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
            }
            
            log.error("Could not extract image data from Banana result: {}", result);
            throw new FalAiException("Invalid response format from Nano Banana - no image URL or data found");
            
        } catch (Exception e) {
            log.error("Error extracting image from Banana response", e);
            throw new FalAiException("Failed to extract image from Nano Banana API response: " + e.getMessage(), e);
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
            throw new FalAiException("Failed to download image from Nano Banana URL: " + imageUrl, e);
        }
    }
    
    /**
     * Sanitize Banana/Fal.ai API errors into user-friendly messages
     */
    private String sanitizeBananaError(ai.fal.client.exception.FalException e) {
        String errorMessage = e.getMessage();
        
        // Extract error code if available
        if (errorMessage.contains("422")) {
            return "Unable to process the request. The image or prompt may be invalid. Please try:\n" +
                   "- Using a different reference image\n" +
                   "- Modifying your prompt\n" +
                   "- Ensuring the image is a standard format (PNG, JPEG)";
        } else if (errorMessage.contains("400")) {
            return "Invalid request. Please check your input and try again.";
        } else if (errorMessage.contains("401") || errorMessage.contains("403")) {
            return "Authentication error with the AI service. Please contact support.";
        } else if (errorMessage.contains("429")) {
            return "Too many requests. Please wait a moment and try again.";
        } else if (errorMessage.contains("500") || errorMessage.contains("503")) {
            return "The AI service is temporarily unavailable. Please try again in a few moments.";
        }
        
        // Generic fallback
        return "Failed to generate with Nano Banana. Please try again or use a different prompt/image.";
    }
    
    @Override
    public String getModelName() {
        return "Nano Banana (via fal.ai)";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Banana uses fal.ai infrastructure, check if client is configured
            return falClient != null;
        } catch (Exception e) {
            log.warn("Banana service is not available: {}", e.getMessage());
            return false;
        }
    }
}


