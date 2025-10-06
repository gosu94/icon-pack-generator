package com.gosu.iconpackgenerator.domain.ai;

import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import ai.fal.client.queue.QueueStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.iconpackgenerator.config.OpenAIConfig;
import com.gosu.iconpackgenerator.exception.FalAiException;
import com.gosu.iconpackgenerator.util.ErrorMessageSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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
public class GptModelService implements AIModelService {

    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    private final OpenAIConfig openAIConfig;
    private final ErrorMessageSanitizer errorMessageSanitizer;
    private final RestTemplate restTemplate;

    private static final String GPT_TEXT_TO_IMAGE_ENDPOINT = "fal-ai/gpt-image-1/text-to-image/byok";

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
            Exception lastException;

            try {
                return attemptGptGeneration(prompt, seed, false);
            } catch (Exception e) {
                lastException = e;
                String errorMessage = e.getMessage();
                log.warn("First GPT generation attempt failed, checking error type: {}", errorMessage);

                // Handle 422 (content policy) errors - no retry
                if (errorMessageSanitizer.is422Error(errorMessage)) {
                    log.error("GPT generation failed with 422 error, likely content policy violation.", e);
                    throw new FalAiException("Your request could not be processed as it may violate content policy. Please try a different prompt. You have been refunded for this attempt.", e);
                }

                // Handle 5xx and other temporary errors - retry once
                if (isLikelyTemporaryFailure(errorMessage)) {
                    log.info("Likely temporary failure detected, attempting retry with same parameters");
                    try {
                        return attemptGptGeneration(prompt, seed, true);
                    } catch (Exception retryException) {
                        log.error("Retry attempt also failed", retryException);
                        lastException = retryException;
                    }
                }
            }

            // All attempts failed or a non-retriable error occurred
            String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(lastException.getMessage(), "GPT");

            if (isLikelyTemporaryFailure(lastException.getMessage())) {
                sanitizedMessage = "Service is temporarily unavailable. " + sanitizedMessage;
            }

            throw new FalAiException(sanitizedMessage, lastException);
        });
    }

    private byte[] attemptGptGeneration(String prompt, Long seed, boolean isRetry) throws Exception {
        log.info("Generating GPT image with endpoint: {} (seed: {}, retry: {})", GPT_TEXT_TO_IMAGE_ENDPOINT, seed, isRetry);

        // Apply GPT-specific styling to the prompt with explicit constraints
        // Use the same prompt for both initial attempt and retry
        String gptPrompt = prompt + " - clean icon design, no text, no labels, no grid lines, no borders, transparent background";

        Map<String, Object> input = createGptTextToImageInputMap(gptPrompt, seed);
        log.info("Making GPT text-to-image API call with input keys: {} (seed: {}, retry: {})", input.keySet(), seed, isRetry);

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

        log.debug("GPT text-to-image input parameters: {}", input);
        return input;
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
            Exception lastException;

            try {
                return attemptGptImageToImageGeneration(prompt, sourceImageData, seed, false);
            } catch (Exception e) {
                lastException = e;
                String errorMessage = e.getMessage();
                log.warn("First GPT image-to-image attempt failed, checking error type: {}", errorMessage);

                // Handle 422 (content policy) errors - no retry
                if (errorMessageSanitizer.is422Error(errorMessage)) {
                    log.error("GPT image-to-image generation failed with 422 error, likely content policy violation.", e);
                    throw new FalAiException("Your request could not be processed as it may violate content policy. Please try a different prompt. You have been refunded for this attempt.", e);
                }
                // Handle 5xx and other temporary errors - retry once
                if (isLikelyTemporaryFailure(errorMessage)) {
                    log.info("Likely temporary failure detected, attempting image-to-image retry with same parameters");
                    try {
                        return attemptGptImageToImageGeneration(prompt, sourceImageData, seed, true);
                    } catch (Exception retryException) {
                        log.error("Image-to-image retry attempt also failed", retryException);
                        lastException = retryException;
                    }
                }
            }

            // All attempts failed or a non-retriable error occurred
            String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(lastException.getMessage(), "GPT");

            if (isLikelyTemporaryFailure(lastException.getMessage())) {
                sanitizedMessage = "Service is temporarily unavailable. " + sanitizedMessage;
            }

            throw new FalAiException(sanitizedMessage, lastException);
        });
    }

    //We have to use OpenAI API because fal.ai does not officially support transparent background option (at this point)
    private byte[] attemptGptImageToImageGeneration(String prompt, byte[] sourceImageData, Long seed, boolean isRetry) throws Exception {
        log.info("Generating GPT image-to-image with OpenAI API (seed: {}, retry: {})", seed, isRetry);

        validateOpenAIConfiguration();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(openAIConfig.getApiKey());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.IMAGE_PNG);
        imageHeaders.setContentDispositionFormData("image", "image.png");
        HttpEntity<byte[]> imageEntity = new HttpEntity<>(sourceImageData, imageHeaders);
        body.add("image", imageEntity);
        
        body.add("model", "gpt-image-1");
        body.add("prompt", prompt);
        body.add("size", "1024x1024");
        body.add("quality", "high");
        body.add("output_format", "png");
        body.add("background", "transparent");
        body.add("input_fidelity", "low");
        body.add("n", 1);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info("Making OpenAI API call for image editing (seed: {}, retry: {})", seed, isRetry);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "https://api.openai.com/v1/images/edits",
                    HttpMethod.POST,
                    entity,
                    JsonNode.class
            );

            JsonNode responseBody = response.getBody();
            log.debug("Received OpenAI API response: {}", responseBody);

            if (responseBody == null) {
                throw new FalAiException("Empty response from OpenAI API");
            }

            return extractImageFromOpenAIResponse(responseBody);

        } catch (Exception e) {
            log.error("Error calling OpenAI image editing API", e);
            throw new FalAiException("Failed to generate image with OpenAI API: " + e.getMessage(), e);
        }
    }


    private byte[] extractImageFromResult(JsonNode result) {
        try {
            // GPT Image likely returns images in the 'images' array (following fal.ai pattern)
            JsonNode imagesNode = result.path("images");
            if (imagesNode.isArray() && !imagesNode.isEmpty()) {
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

    /**
     * Extract image data from OpenAI API response
     * gpt-image-1 always returns base64-encoded images directly
     */
    private byte[] extractImageFromOpenAIResponse(JsonNode response) {
        try {
            // OpenAI API returns images in the 'data' array
            JsonNode dataNode = response.path("data");
            if (dataNode.isArray() && !dataNode.isEmpty()) {
                JsonNode firstImage = dataNode.get(0);
                
                // gpt-image-1 always returns base64 images directly in b64_json field
                String base64Data = firstImage.path("b64_json").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in OpenAI response");
                    return Base64.getDecoder().decode(base64Data);
                }
                
                // For other models (dall-e-2, dall-e-3) that might return URLs
                String imageUrl = firstImage.path("url").asText();
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from OpenAI URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
            }

            log.error("Could not extract image data from OpenAI response: {}", response);
            throw new FalAiException("Invalid response format from OpenAI API - no image data found");

        } catch (Exception e) {
            log.error("Error extracting image from OpenAI response", e);
            throw new FalAiException("Failed to extract image from OpenAI API response: " + e.getMessage(), e);
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
        return "GPT Image 1 (OpenAI API)";
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

    /**
     * Checks if an error message indicates a temporary service failure
     */
    private boolean isLikelyTemporaryFailure(String errorMessage) {
        log.info("Checking for temporary failure");
        if (errorMessage == null) {
            return false;
        }

        String lowerMessage = errorMessage.toLowerCase();

        return lowerMessage.contains("timeout") ||
                lowerMessage.contains("connection") ||
                lowerMessage.contains("network") ||
                lowerMessage.contains("server error") ||
                lowerMessage.contains("internal error") ||
                lowerMessage.contains("stream was reset") ||
                lowerMessage.contains("service unavailable") ||
                lowerMessage.contains("temporarily unavailable") ||
                lowerMessage.contains("429") || // Rate limiting
                lowerMessage.contains("502") || // Bad Gateway
                lowerMessage.contains("503") || // Service Unavailable
                lowerMessage.contains("504");   // Gateway Timeout
    }
}
