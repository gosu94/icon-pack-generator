package com.gosu.iconpackgenerator.domain.ai;

import com.fasterxml.jackson.databind.JsonNode;
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
public class Gpt15ModelService implements AIModelService {

    private static final String OPENAI_MODEL = "gpt-image-1.5";

    private final OpenAIConfig openAIConfig;
    private final ErrorMessageSanitizer errorMessageSanitizer;
    private final RestTemplate restTemplate;

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
                validateOpenAIConfiguration();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(openAIConfig.getApiKey());

                Map<String, Object> input = createTextToImageInputMap(prompt);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(input, headers);

                log.info("Calling OpenAI GPT-1.5-style text-to-image with input keys {} (seed: {})",
                        input.keySet(), seed);

                ResponseEntity<JsonNode> response = restTemplate.exchange(
                        "https://api.openai.com/v1/images/generations",
                        HttpMethod.POST,
                        entity,
                        JsonNode.class
                );

                JsonNode responseBody = response.getBody();
                if (responseBody == null) {
                    throw new FalAiException("Empty response from OpenAI API");
                }

                return extractImageFromOpenAIResponse(responseBody);
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
        log.info("Generating GPT-1.5 image-to-image for prompt: {} (seed: {})", prompt, seed);

        return generateImageToImageAsync(prompt, sourceImageData, seed)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating GPT-1.5 image-to-image", error);
                    } else if (bytes != null) {
                        log.info("Successfully generated GPT-1.5 image-to-image, size: {} bytes", bytes.length);
                    }
                });
    }

    private CompletableFuture<byte[]> generateImageToImageAsync(String prompt, byte[] sourceImageData, Long seed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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
                body.add("model", OPENAI_MODEL);
                body.add("prompt", prompt);
                body.add("size", "1024x1024");
                body.add("quality", "high");
                body.add("output_format", "png");
                body.add("background", "transparent");
                body.add("input_fidelity", "high");
                body.add("n", 1);

                HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

                log.info("Calling OpenAI GPT-1.5-style image-to-image with seed: {}", seed);

                ResponseEntity<JsonNode> response = restTemplate.exchange(
                        "https://api.openai.com/v1/images/edits",
                        HttpMethod.POST,
                        entity,
                        JsonNode.class
                );

                JsonNode responseBody = response.getBody();
                if (responseBody == null) {
                    throw new FalAiException("Empty response from OpenAI API");
                }

                return extractImageFromOpenAIResponse(responseBody);
            } catch (Exception e) {
                log.error("Error calling GPT-1.5 image-to-image API", e);
                String sanitized = errorMessageSanitizer.sanitizeErrorMessage(e.getMessage(), "GPT1.5");
                throw new FalAiException(sanitized, e);
            }
        });
    }

    private Map<String, Object> createTextToImageInputMap(String prompt) {
        Map<String, Object> input = new HashMap<>();
        input.put("model", OPENAI_MODEL);
        input.put("prompt", prompt);
        input.put("size", "1024x1024");
        input.put("background", "transparent");
        input.put("quality", "high");
        input.put("n", 1);
        input.put("output_format", "png");
        return input;
    }

    private byte[] extractImageFromOpenAIResponse(JsonNode response) {
        try {
            JsonNode dataNode = response.path("data");
            if (dataNode.isArray() && !dataNode.isEmpty()) {
                JsonNode firstImage = dataNode.get(0);

                String base64Data = firstImage.path("b64_json").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in OpenAI response");
                    return Base64.getDecoder().decode(base64Data);
                }

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

    @Override
    public String getModelName() {
        return "GPT Image 1.5 style (OpenAI API)";
    }

    @Override
    public boolean isAvailable() {
        try {
            validateOpenAIConfiguration();
            return true;
        } catch (Exception e) {
            log.warn("GPT-1.5 service is not available: {}", e.getMessage());
            return false;
        }
    }

    private void validateOpenAIConfiguration() {
        if (openAIConfig.getApiKey() == null || openAIConfig.getApiKey().trim().isEmpty()) {
            throw new FalAiException("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable or openai.api-key property.");
        }

        if ("your-openai-api-key-here".equals(openAIConfig.getApiKey())) {
            throw new FalAiException("OpenAI API key is still set to default value. Please provide a valid API key.");
        }
    }
}
