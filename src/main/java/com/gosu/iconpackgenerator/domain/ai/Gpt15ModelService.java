package com.gosu.iconpackgenerator.domain.ai;

import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.iconpackgenerator.domain.icons.model.AIModelConfig;
import com.gosu.iconpackgenerator.exception.FalAiException;
import com.gosu.iconpackgenerator.util.ErrorMessageSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class Gpt15ModelService implements AIModelService {

    private static final String TEXT_TO_IMAGE_ENDPOINT = "fal-ai/gpt-image-1.5";
    private static final String IMAGE_TO_IMAGE_ENDPOINT = "fal-ai/gpt-image-1.5/edit";

    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    private final ErrorMessageSanitizer errorMessageSanitizer;
    private final AIModelConfig aiModelConfig;

    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        return generateImage(prompt, null);
    }

    public CompletableFuture<byte[]> generateImage(String prompt, Long seed) {
        log.info("Generating GPT-1.5 image for prompt: {} (seed: {})", prompt, seed);

        return generateImageAsync(prompt, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with GPT-1.5", error);
                    } else if (bytes != null) {
                        log.info("Successfully generated GPT-1.5 image, size: {} bytes", bytes.length);
                    }
                });
    }

    private CompletableFuture<byte[]> generateImageAsync(String prompt, Long seed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateConfiguration();

                Map<String, Object> input = createTextToImageInputMap(prompt);
                log.info("Calling GPT-1.5 text-to-image endpoint {} with input keys {} (seed: {})",
                        TEXT_TO_IMAGE_ENDPOINT, input.keySet(), seed);

                Output<JsonObject> output = falClient.subscribe(TEXT_TO_IMAGE_ENDPOINT,
                        SubscribeOptions.<JsonObject>builder()
                                .input(input)
                                .logs(true)
                                .resultType(JsonObject.class)
                                .build()
                );

                JsonObject result = output.getData();
                JsonNode jsonResult = objectMapper.readTree(result.toString());

                return extractImageFromResult(jsonResult);
            } catch (Exception e) {
                log.error("Error calling GPT-1.5 text-to-image API", e);
                String sanitized = errorMessageSanitizer.sanitizeErrorMessage(e.getMessage(), "GPT1.5");
                throw new FalAiException(sanitized, e);
            }
        });
    }

    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData) {
        return generateImageToImage(prompt, sourceImageData, null);
    }

    public CompletableFuture<byte[]> generateImageToImage(String prompt, byte[] sourceImageData, Long seed) {
        return generateImageToImage(prompt, sourceImageData, seed, null);
    }

    public CompletableFuture<byte[]> generateImageToImage(String prompt,
                                                          byte[] sourceImageData,
                                                          Long seed,
                                                          Gpt15ImageOptions options) {
        log.info("Generating GPT-1.5 image-to-image for prompt: {} (seed: {})", prompt, seed);

        return generateImageToImageAsync(prompt, sourceImageData, seed, options)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating GPT-1.5 image-to-image", error);
                    } else if (bytes != null) {
                        log.info("Successfully generated GPT-1.5 image-to-image, size: {} bytes", bytes.length);
                    }
                });
    }

    private CompletableFuture<byte[]> generateImageToImageAsync(String prompt,
                                                                byte[] sourceImageData,
                                                                Long seed,
                                                                Gpt15ImageOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateConfiguration();

                String imageDataUrl = convertToDataUrl(sourceImageData);
                Map<String, Object> input = createImageToImageInputMap(prompt, imageDataUrl, options);
                log.info("Calling GPT-1.5 image-to-image endpoint {} with input keys {} (seed: {})",
                        IMAGE_TO_IMAGE_ENDPOINT, input.keySet(), seed);

                Output<JsonObject> output = falClient.subscribe(IMAGE_TO_IMAGE_ENDPOINT,
                        SubscribeOptions.<JsonObject>builder()
                                .input(input)
                                .logs(true)
                                .resultType(JsonObject.class)
                                .build()
                );

                JsonObject result = output.getData();
                JsonNode jsonResult = objectMapper.readTree(result.toString());

                return extractImageFromResult(jsonResult);
            } catch (Exception e) {
                log.error("Error calling GPT-1.5 image-to-image API", e);
                String sanitized = errorMessageSanitizer.sanitizeErrorMessage(e.getMessage(), "GPT1.5");
                throw new FalAiException(sanitized, e);
            }
        });
    }

    private Map<String, Object> createTextToImageInputMap(String prompt) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("image_size", "1024x1024");
        input.put("background", "transparent");
        input.put("quality", "high");
        input.put("num_images", 1);
        input.put("output_format", "png");
        return input;
    }

    private Map<String, Object> createImageToImageInputMap(String prompt, String imageDataUrl) {
        return createImageToImageInputMap(prompt, imageDataUrl, null);
    }

    private Map<String, Object> createImageToImageInputMap(String prompt,
                                                           String imageDataUrl,
                                                           Gpt15ImageOptions options) {
        String imageSize = options != null && options.getImageSize() != null
                ? options.getImageSize()
                : "1024x1024";
        String background = options != null && options.getBackground() != null
                ? options.getBackground()
                : "transparent";
        String quality = options != null && options.getQuality() != null
                ? options.getQuality()
                : "high";
        String outputFormat = options != null && options.getOutputFormat() != null
                ? options.getOutputFormat()
                : "png";
        String inputFidelity = options != null && options.getInputFidelity() != null
                ? options.getInputFidelity()
                : "high";

        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("image_urls", Collections.singletonList(imageDataUrl));
        input.put("image_size", imageSize);
        input.put("background", background);
        input.put("quality", quality);
        input.put("input_fidelity", inputFidelity);
        input.put("num_images", 1);
        input.put("output_format", outputFormat);
        return input;
    }

    private byte[] extractImageFromResult(JsonNode result) {
        try {
            JsonNode imagesNode = result.path("images");
            if (imagesNode.isArray() && imagesNode.size() > 0) {
                JsonNode firstImage = imagesNode.get(0);
                String imageUrl = firstImage.path("url").asText();

                if (!imageUrl.isEmpty()) {
                    log.info("Downloading GPT-1.5 image from URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }

                String base64Data = firstImage.path("base64").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in GPT-1.5 response");
                    return Base64.getDecoder().decode(base64Data);
                }

                String dataUrl = firstImage.path("data").asText();
                if (dataUrl.startsWith("data:image/")) {
                    log.debug("Found data URL in GPT-1.5 response");
                    String base64Part = dataUrl.substring(dataUrl.indexOf(",") + 1);
                    return Base64.getDecoder().decode(base64Part);
                }
            }

            log.error("Could not extract image data from GPT-1.5 result: {}", result);
            throw new FalAiException("Invalid response format from GPT-1.5 - no image data found");
        } catch (Exception e) {
            log.error("Error extracting image from GPT-1.5 response", e);
            throw new FalAiException("Failed to extract image from GPT-1.5 response: " + e.getMessage(), e);
        }
    }

    private byte[] downloadImageFromUrl(String imageUrl) {
        try {
            URL url = URI.create(imageUrl).toURL();
            try (InputStream inputStream = url.openStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            log.error("Failed to download GPT-1.5 image from URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download GPT-1.5 image from URL: " + imageUrl, e);
        }
    }

    private String convertToDataUrl(byte[] imageData) {
        try {
            String base64Data = Base64.getEncoder().encodeToString(imageData);
            return "data:image/png;base64," + base64Data;
        } catch (Exception e) {
            log.error("Error converting GPT-1.5 source image to data URL", e);
            throw new FalAiException("Failed to convert image to data URL", e);
        }
    }

    @Override
    public String getModelName() {
        return TEXT_TO_IMAGE_ENDPOINT;
    }

    @Override
    public boolean isAvailable() {
        try {
            validateConfiguration();
            return true;
        } catch (Exception e) {
            log.warn("GPT-1.5 service is not available: {}", e.getMessage());
            return false;
        }
    }

    private void validateConfiguration() {
        String apiKey = aiModelConfig.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new FalAiException("Fal.ai API key is not configured. Please set FAL_KEY environment variable or fal.ai.api-key property.");
        }

        if ("your-fal-api-key-here".equals(apiKey)) {
            throw new FalAiException("Fal.ai API key is still set to default value. Please provide a valid API key.");
        }

        if (!isValidApiKeyFormat(apiKey)) {
            throw new FalAiException("Fal.ai API key format appears to be invalid. Expected format: key_id:key_secret");
        }
    }

    private boolean isValidApiKeyFormat(String apiKey) {
        return apiKey.contains(":") && apiKey.split(":").length == 2 &&
                apiKey.split(":")[0].length() > 10 && apiKey.split(":")[1].length() > 10;
    }
}
