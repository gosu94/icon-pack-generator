package com.gosu.iconpackgenerator.domain.service;

import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import ai.fal.client.queue.QueueStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.iconpackgenerator.config.OpenAIConfig;
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
public class GptModelService implements AIModelService {
    
    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    private final OpenAIConfig openAIConfig;
    
    private static final String GPT_TEXT_TO_IMAGE_ENDPOINT = "fal-ai/gpt-image-1/text-to-image/byok";
    private static final String GPT_IMAGE_TO_IMAGE_ENDPOINT = "fal-ai/gpt-image-1/edit-image/byok";
    
    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        return generateImage(prompt, null);
    }
    
    /**
     * Generate image with optional seed for reproducible results
     */
    public CompletableFuture<byte[]> generateImage(String prompt, Long seed) {
        log.info("Generating image with GPT Image for prompt: {} (seed: {})", prompt, seed);
        
        return generateGptImageAsync(prompt, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with GPT Image", error);
                    } else {
                        log.info("Successfully generated image with GPT Image, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateGptImageAsync(String prompt, Long seed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating GPT image with endpoint: {} (seed: {})", GPT_TEXT_TO_IMAGE_ENDPOINT, seed);
                
                // Apply GPT-specific styling to the prompt with explicit constraints
                String gptPrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders, transparent background";
                
                Map<String, Object> input = createGptTextToImageInputMap(gptPrompt, seed);
                log.info("Making GPT text-to-image API call with input keys: {} (seed: {})", input.keySet(), seed);
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(GPT_TEXT_TO_IMAGE_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("GPT generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from GPT API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted GPT result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (Exception e) {
                log.error("Error calling GPT API", e);
                throw new FalAiException("Failed to generate image with GPT Image: " + e.getMessage(), e);
            }
        });
    }
    
    private Map<String, Object> createGptTextToImageInputMap(String prompt, Long seed) {
        validateOpenAIConfiguration();
        

        
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("image_size", "1024x1024"); // Use 1024x1024 as specified
        input.put("num_images", 1); // Always generate 1 image
        input.put("quality", "auto"); // Use auto quality as specified
        input.put("background", "transparent"); // Use transparent background as specified
        input.put("openai_api_key", openAIConfig.getApiKey());
        
        // Note: GPT Image doesn't seem to support seeds based on the schema, but we'll add it if provided
        // and let the API ignore it if not supported
        if (seed != null) {
            input.put("seed", seed);
        }
        
        log.debug("GPT text-to-image input parameters: {}", input);
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
        log.info("Generating image-to-image with GPT Image for prompt: {}", prompt);
        
        return generateGptImageToImageAsync(prompt, sourceImageData, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image-to-image with GPT Image", error);
                    } else {
                        log.info("Successfully generated image-to-image with GPT Image, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateGptImageToImageAsync(String prompt, byte[] sourceImageData, Long seed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating GPT image-to-image with endpoint: {} (seed: {})", GPT_IMAGE_TO_IMAGE_ENDPOINT, seed);
                
                // Convert image data to data URL for image_urls parameter
                String imageDataUrl = convertToDataUrl(sourceImageData);
                

                Map<String, Object> input = createGptImageToImageInputMap(prompt, imageDataUrl, seed);
                log.info("Making GPT image-to-image API call with input keys: {} (seed: {})", input.keySet(), seed);
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(GPT_IMAGE_TO_IMAGE_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("GPT image-to-image generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from GPT image-to-image API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted GPT image-to-image result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (Exception e) {
                log.error("Error calling GPT image-to-image API", e);
                throw new FalAiException("Failed to generate image-to-image with GPT Image: " + e.getMessage(), e);
            }
        });
    }
    
    private Map<String, Object> createGptImageToImageInputMap(String prompt, String imageDataUrl, Long seed) {
        validateOpenAIConfiguration();
        
        Map<String, Object> input = new HashMap<>();
        input.put("image_urls", List.of(imageDataUrl)); // GPT uses image_urls list for image-to-image
        input.put("prompt", prompt);
        input.put("image_size", "1024x1024"); // Use 1024x1024 as specified
        input.put("num_images", 1); // Always generate 1 image
        input.put("quality", "auto"); // Use auto quality as specified
        input.put("input_fidelity", "low"); // Default input fidelity
        input.put("openai_api_key", openAIConfig.getApiKey());
        
        // Note: GPT Image doesn't seem to support seeds based on the schema, but we'll add it if provided
        // and let the API ignore it if not supported
        if (seed != null) {
            input.put("seed", seed);
        }
        
        log.debug("GPT image-to-image input parameters: {}", input);
        return input;
    }
    
    private byte[] extractImageFromResult(JsonNode result) {
        try {
            // GPT Image likely returns images in the 'images' array (following fal.ai pattern)
            JsonNode imagesNode = result.path("images");
            if (imagesNode.isArray() && imagesNode.size() > 0) {
                JsonNode firstImage = imagesNode.get(0);
                String imageUrl = firstImage.path("url").asText();
                
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from GPT Image URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
                
                // Check if there's direct base64 data (alternative format)
                String base64Data = firstImage.path("base64").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in GPT Image response");
                    return Base64.getDecoder().decode(base64Data);
                }
                
                // Check for data URL format
                String dataUrl = firstImage.path("data").asText();
                if (dataUrl.startsWith("data:image/")) {
                    log.debug("Found data URL in GPT Image response");
                    String base64Part = dataUrl.substring(dataUrl.indexOf(",") + 1);
                    return Base64.getDecoder().decode(base64Part);
                }
            }
            
            // Check if the result has a direct image field
            JsonNode imageNode = result.path("image");
            if (imageNode != null && !imageNode.isMissingNode()) {
                String imageUrl = imageNode.path("url").asText();
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from GPT Image URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
            }
            
            log.error("Could not extract image data from GPT Image result: {}", result);
            throw new FalAiException("Invalid response format from GPT Image - no image URL or data found");
            
        } catch (Exception e) {
            log.error("Error extracting image from GPT Image response", e);
            throw new FalAiException("Failed to extract image from GPT Image API response: " + e.getMessage(), e);
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
                log.info("Successfully downloaded image from GPT Image: {} bytes", imageData.length);
                return imageData;
                
            }
        } catch (IOException e) {
            log.error("Failed to download image from GPT Image URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download image from GPT Image URL: " + imageUrl, e);
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
    
    private void validateOpenAIConfiguration() {
        if (openAIConfig.getApiKey() == null || openAIConfig.getApiKey().trim().isEmpty()) {
            throw new FalAiException("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable or openai.api-key property.");
        }
        
        if (openAIConfig.getApiKey().equals("your-openai-api-key-here")) {
            throw new FalAiException("OpenAI API key is still set to default value. Please provide a valid API key.");
        }
        
        log.debug("OpenAI configuration validated successfully. API key format: valid");
    }
    
    @Override
    public String getModelName() {
        return "GPT Image (via fal.ai)";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            validateOpenAIConfiguration();
            return true;
        } catch (Exception e) {
            log.warn("GPT Image service is not available: {}", e.getMessage());
            return false;
        }
    }
}
