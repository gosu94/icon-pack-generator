package com.gosu.icon_pack_generator.service;

import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.icon_pack_generator.exception.FalAiException;
import com.gosu.icon_pack_generator.model.AIModelConfig;
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
public class FluxModelService implements AIModelService {
    
    private final AIModelConfig config;
    private final ObjectMapper objectMapper;
    private final FalClient falClient;
    
    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        log.info("Generating image with Fal.ai model for prompt: {}", prompt);
        
        return generateImageAsync(prompt)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image", error);
                    } else {
                        log.info("Successfully generated image, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateImageAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate configuration before making API call
                validateConfiguration();
                
                Map<String, Object> input = createInputMap(prompt);
                log.info("Making fal.ai API call to endpoint: {} with input keys: {}",
                        config.getModelEndpoint(), input.keySet());
                
                // Using the fal.ai client API with SubscribeOptions as per documentation
                Output<JsonObject> output = falClient.subscribe(config.getModelEndpoint(),
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .build()
                );
                log.debug("Received output from fal.ai API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (Exception e) {
                log.error("Error calling fal.ai API", e);
                throw new FalAiException("Failed to generate image with fal.ai: " + getDetailedErrorMessage(e), e);
            }
        });
    }
    
    private Map<String, Object> createInputMap(String prompt) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("aspect_ratio", config.getAspectRatio());
        input.put("num_images", config.getNumImages());
        input.put("enable_safety_checker", config.isEnableSafetyChecker());
        input.put("output_format", config.getOutputFormat());
        input.put("safety_tolerance", config.getSafetyTolerance());
        
        // Add some additional parameters for better icon generation
        input.put("guidance_scale", 3.5);
        input.put("seed", generateRandomSeed());
        
        log.debug("Input parameters: {}", input);
        return input;
    }
    
    private byte[] extractImageFromResult(JsonNode result) {
        try {
            // fal.ai returns images in the 'images' array
            JsonNode imagesNode = result.path("images");
            if (imagesNode.isArray() && imagesNode.size() > 0) {
                JsonNode firstImage = imagesNode.get(0);
                String imageUrl = firstImage.path("url").asText();
                
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from fal.ai URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
                
                // Check if there's direct base64 data (alternative format)
                String base64Data = firstImage.path("base64").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in response");
                    return Base64.getDecoder().decode(base64Data);
                }
                
                // Check for data URL format
                String dataUrl = firstImage.path("data").asText();
                if (dataUrl.startsWith("data:image/")) {
                    log.debug("Found data URL in response");
                    String base64Part = dataUrl.substring(dataUrl.indexOf(",") + 1);
                    return Base64.getDecoder().decode(base64Part);
                }
            }
            
            log.error("Could not extract image data from result: {}", result);
            throw new FalAiException("Invalid response format from fal.ai - no image URL or data found");
            
        } catch (Exception e) {
            log.error("Error extracting image from fal.ai response", e);
            throw new FalAiException("Failed to extract image from API response: " + e.getMessage(), e);
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
                log.info("Successfully downloaded image: {} bytes", imageData.length);
                return imageData;
                
            }
        } catch (IOException e) {
            log.error("Failed to download image from URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download image from fal.ai URL: " + imageUrl, e);
        }
    }
    
    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }

    /**
     * Generate image with custom endpoint and input map (used by other services)
     */
    public byte[] generateImageWithCustomEndpoint(String endpoint, Map<String, Object> input) {
        try {
            validateConfiguration();

            log.info("Making fal.ai API call to custom endpoint: {} with input keys: {}", endpoint, input.keySet());

            Output<JsonObject> output = falClient.subscribe(endpoint,
                SubscribeOptions.<JsonObject>builder()
                    .input(input)
                    .logs(true)
                    .resultType(JsonObject.class)
                    .build()
            );
            log.debug("Received output from fal.ai custom endpoint API: {}", output);

            JsonObject result = output.getData();
            log.debug("Extracted result: {}", result);

            JsonNode jsonResult = objectMapper.readTree(result.toString());

            return extractImageFromResult(jsonResult);

        } catch (Exception e) {
            String detailedError = createDetailedErrorMessage(e, endpoint, input);
            log.error("Error calling fal.ai custom endpoint API: {}", detailedError, e);
            throw new FalAiException(detailedError, e);
        }
    }
    
    /**
     * Generate image using image-to-image functionality for consistency
     */
    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData) {
        log.info("Generating image-to-image with Fal.ai for prompt: {}", 
                prompt.substring(0, Math.min(100, prompt.length())));
        
        return generateImageToImageAsync(prompt, sourceImageData)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image-to-image", error);
                    } else {
                        log.info("Successfully generated image-to-image, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateImageToImageAsync(String prompt, byte[] sourceImageData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateConfiguration();
                
                // Convert image data to data URL for image_url parameter
                String imageDataUrl = convertToDataUrl(sourceImageData);
                
                Map<String, Object> input = createImageToImageInputMap(prompt, imageDataUrl);
                log.info("Making fal.ai image-to-image API call to endpoint: {} with input keys: {}", 
                        "fal-ai/flux-pro/v1.1/redux", input.keySet());
                
                Output<JsonObject> output = falClient.subscribe("fal-ai/flux-pro/v1.1/redux",
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .build()
                );
                log.debug("Received output from fal.ai image-to-image API: {}", output);
                
                JsonObject result = output.getData();
                log.debug("Extracted result: {}", result);
                
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractImageFromResult(jsonResult);
                
            } catch (Exception e) {
                log.error("Error calling fal.ai image-to-image API", e);
                throw new FalAiException("Failed to generate image-to-image with fal.ai: " + e.getMessage(), e);
            }
        });
    }
    
    private Map<String, Object> createImageToImageInputMap(String prompt, String imageDataUrl) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("image_url", imageDataUrl);  // This is the key parameter for image-to-image!
        input.put("aspect_ratio", config.getAspectRatio());
        input.put("num_images", config.getNumImages());
        input.put("enable_safety_checker", config.isEnableSafetyChecker());
        input.put("output_format", config.getOutputFormat());
        input.put("safety_tolerance", config.getSafetyTolerance());
        
        // Image-to-image specific parameters
        input.put("guidance_scale", 3.5);
        input.put("seed", generateRandomSeed());
        
        log.debug("Image-to-image input parameters: {}", input);
        return input;
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
        return config.getModelEndpoint();
    }
    
    @Override
    public boolean isAvailable() {
        try {
            validateConfiguration();
            return true;
        } catch (Exception e) {
            log.warn("Fal.ai service is not available: {}", e.getMessage());
            return false;
        }
    }
    
    private void validateConfiguration() {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new FalAiException("Fal.ai API key is not configured. Please set FAL_KEY environment variable or fal.ai.api-key property.");
        }
        
        if (config.getApiKey().equals("your-fal-api-key-here")) {
            throw new FalAiException("Fal.ai API key is still set to default value. Please provide a valid API key.");
        }
        
        if (config.getModelEndpoint() == null || config.getModelEndpoint().trim().isEmpty()) {
            throw new FalAiException("Fal.ai model endpoint is not configured.");
        }
        
        // Validate API key format (fal.ai keys typically have a specific format)
        if (!isValidApiKeyFormat(config.getApiKey())) {
            throw new FalAiException("Fal.ai API key format appears to be invalid. Expected format: key_id:key_secret");
        }
        
        log.debug("Fal.ai configuration validated successfully. Endpoint: {}, API key format: valid", 
                config.getModelEndpoint());
    }
    
    private boolean isValidApiKeyFormat(String apiKey) {
        // fal.ai API keys typically follow the format: key_id:key_secret
        return apiKey.contains(":") && apiKey.split(":").length == 2 && 
               apiKey.split(":")[0].length() > 10 && apiKey.split(":")[1].length() > 10;
    }
    
    private String getDetailedErrorMessage(Exception e) {
        String message = e.getMessage();
        
        if (message != null) {
            if (message.contains("401") || message.contains("Unauthorized")) {
                return "Authentication failed (401 Unauthorized). Possible causes:\n" +
                       "1. Invalid API key\n" +
                       "2. API key doesn't have required permissions\n" +
                       "3. API key has expired\n" +
                       "4. Incorrect API key format\n" +
                       "Current API key format: " + getMaskedApiKey() + "\n" +
                       "Please verify your fal.ai API key at https://fal.ai/dashboard";
            }
            
            if (message.contains("403") || message.contains("Forbidden")) {
                return "Access forbidden (403). Your API key doesn't have permission to access this endpoint: " + 
                       config.getModelEndpoint();
            }
            
            if (message.contains("429") || message.contains("Rate limit")) {
                return "Rate limit exceeded (429). Please wait before making more requests.";
            }
            
            if (message.contains("500") || message.contains("Internal Server Error")) {
                return "Fal.ai server error (500). Please try again later.";
            }
            
            if (message.contains("400") || message.contains("Bad Request")) {
                return "Bad request (400). Check your input parameters and model endpoint.";
            }
        }
        
        return "API call failed: " + (message != null ? message : "Unknown error");
    }
    
    private String getMaskedApiKey() {
        if (config.getApiKey() == null || config.getApiKey().length() < 8) {
            return "[INVALID_KEY]";
        }
        
        if (config.getApiKey().contains(":")) {
            String[] parts = config.getApiKey().split(":");
            return parts[0].substring(0, Math.min(8, parts[0].length())) + "***:" + 
                   "***" + (parts[1].length() > 4 ? parts[1].substring(parts[1].length() - 4) : "***");
        }
        
        return config.getApiKey().substring(0, 8) + "***";
    }

    /**
     * Create a detailed error message for API failures with parameter information
     */
    private String createDetailedErrorMessage(Exception e, String endpoint, Map<String, Object> input) {
        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("Failed to generate image with endpoint '").append(endpoint).append("': ");
        
        // Extract status code and details from FalException
        if (e instanceof ai.fal.client.exception.FalException) {
            ai.fal.client.exception.FalException falEx = (ai.fal.client.exception.FalException) e;
            String message = falEx.getMessage();
            
            if (message.contains("Request failed with code: 422")) {
                errorMsg.append("HTTP 422 Unprocessable Entity - Invalid request parameters. ");
                errorMsg.append("This usually means the request parameters are incorrect or incompatible. ");
                
                // Log the parameters that caused the issue
                errorMsg.append("Request parameters: ");
                input.forEach((key, value) -> {
                    String valueStr = value != null ? value.toString() : "null";
                    // Truncate long values for readability
                    if (valueStr.length() > 100) {
                        valueStr = valueStr.substring(0, 100) + "...";
                    }
                    errorMsg.append(key).append("=").append(valueStr).append(", ");
                });
                
                // Remove trailing comma and space
                if (errorMsg.length() > 2) {
                    errorMsg.setLength(errorMsg.length() - 2);
                }
                
                // Add suggestions for common 422 issues
                errorMsg.append(". Common causes: incompatible image_size/aspect_ratio, invalid style parameters, ");
                errorMsg.append("or model-specific parameter conflicts. Check that all parameters are valid for the '");
                errorMsg.append(endpoint).append("' endpoint.");
            } else {
                errorMsg.append(message);
            }
        } else {
            errorMsg.append(e.getMessage());
        }
        
        return errorMsg.toString();
    }
}
