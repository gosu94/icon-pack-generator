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
public class MinimaxVideoModelService {

    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    
    private static final String MINIMAX_IMAGE_TO_VIDEO_ENDPOINT = "fal-ai/minimax/hailuo-02-fast/image-to-video";
    private static final String DURATION = "6";
    private static final boolean PROMPT_OPTIMIZER = true;
    
    /**
     * Generate video from an image URL and prompt.
     * @param prompt The text prompt describing the video movement/action
     * @param imageUrl The URL of the source image
     * @return CompletableFuture containing the generated video as byte array
     */
    public CompletableFuture<byte[]> generateVideo(String prompt, String imageUrl) {
        log.info("Generating video with Minimax for prompt: {}", prompt);
        
        return generateMinimaxVideoAsync(prompt, imageUrl)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating video with Minimax", error);
                    } else {
                        log.info("Successfully generated video with Minimax, size: {} bytes", bytes.length);
                    }
                });
    }
    
    /**
     * Generate video from image bytes and prompt.
     * Converts the image bytes to a data URI before calling the API.
     * @param prompt The text prompt describing the video movement/action
     * @param imageData The source image as byte array
     * @return CompletableFuture containing the generated video as byte array
     */
    public CompletableFuture<byte[]> generateVideo(String prompt, byte[] imageData) {
        log.info("Generating video with Minimax for prompt: {} with image data ({} bytes)", prompt, imageData.length);
        
        String imageDataUri = convertToDataUri(imageData);
        return generateVideo(prompt, imageDataUri);
    }
    
    private CompletableFuture<byte[]> generateMinimaxVideoAsync(String prompt, String imageUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating Minimax video with endpoint: {}", MINIMAX_IMAGE_TO_VIDEO_ENDPOINT);
                
                Map<String, Object> input = createMinimaxInputMap(prompt, imageUrl);
                log.info("Making Minimax API call with input keys: {}", input.keySet());
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(MINIMAX_IMAGE_TO_VIDEO_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("Minimax video generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from Minimax API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted Minimax result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractVideoFromResult(jsonResult);
                
            } catch (ai.fal.client.exception.FalException e) {
                log.error("Minimax API error: {}", e.getMessage());
                String userFriendlyMessage = sanitizeMinimaxError(e);
                throw new FalAiException(userFriendlyMessage, e);
            } catch (Exception e) {
                log.error("Error calling Minimax API", e);
                throw new FalAiException("Failed to generate video with Minimax. Please try again or use a different prompt/image.", e);
            }
        });
    }
    
    private Map<String, Object> createMinimaxInputMap(String prompt, String imageUrl) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("image_url", imageUrl);
        input.put("duration", DURATION);
        input.put("prompt_optimizer", PROMPT_OPTIMIZER);
        
        log.debug("Minimax image-to-video input parameters: {}", input);
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
    
    private byte[] extractVideoFromResult(JsonNode result) {
        try {
            // Minimax returns video in the 'video' object with 'url' field
            JsonNode videoNode = result.path("video");
            if (videoNode != null && !videoNode.isMissingNode()) {
                String videoUrl = videoNode.path("url").asText();
                
                if (!videoUrl.isEmpty()) {
                    log.info("Downloading video from Minimax URL: {}", videoUrl);
                    return downloadVideoFromUrl(videoUrl);
                }
            }
            
            log.error("Could not extract video data from Minimax result: {}", result);
            throw new FalAiException("Invalid response format from Minimax - no video URL found");
            
        } catch (Exception e) {
            log.error("Error extracting video from Minimax response", e);
            throw new FalAiException("Failed to extract video from Minimax API response: " + e.getMessage(), e);
        }
    }
    
    private byte[] downloadVideoFromUrl(String videoUrl) {
        try {
            log.debug("Downloading video from URL: {}", videoUrl);
            URL url = URI.create(videoUrl).toURL();
            
            try (InputStream inputStream = url.openStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                byte[] videoData = outputStream.toByteArray();
                log.info("Successfully downloaded video from Minimax: {} bytes", videoData.length);
                return videoData;
                
            }
        } catch (IOException e) {
            log.error("Failed to download video from Minimax URL: {}", videoUrl, e);
            throw new FalAiException("Failed to download video from Minimax URL: " + videoUrl, e);
        }
    }
    
    /**
     * Sanitize Minimax/Fal.ai API errors into user-friendly messages
     */
    private String sanitizeMinimaxError(ai.fal.client.exception.FalException e) {
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
        return "Failed to generate video with Minimax. Please try again or use a different prompt/image.";
    }
    
    /**
     * Get the model name
     * @return The name of the AI model
     */
    public String getModelName() {
        return "Minimax Hailuo-02 Fast (via fal.ai)";
    }
    
    /**
     * Check if the service is available
     * @return true if the service is available
     */
    public boolean isAvailable() {
        try {
            // Minimax uses fal.ai infrastructure, check if client is configured
            return falClient != null;
        } catch (Exception e) {
            log.warn("Minimax service is not available: {}", e.getMessage());
            return false;
        }
    }
}

