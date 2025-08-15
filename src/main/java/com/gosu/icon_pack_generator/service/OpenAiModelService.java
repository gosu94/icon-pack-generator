package com.gosu.icon_pack_generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosu.icon_pack_generator.config.OpenAiConfig;
import com.gosu.icon_pack_generator.exception.FalAiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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
public class OpenAiModelService implements AIModelService {
    
    private final OpenAiConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/images/generations";
    
    @Override
    public CompletableFuture<byte[]> generateImage(String prompt) {
        log.info("Generating image with OpenAI DALL-E for prompt: {}", prompt.substring(0, Math.min(100, prompt.length())));
        
        return generateImageAsync(prompt)
                .whenComplete((bytes, error) -> {
                    if (error != null) {
                        log.error("Error generating image with OpenAI", error);
                    } else {
                        log.info("Successfully generated image with OpenAI, size: {} bytes", bytes.length);
                    }
                });
    }
    
    private CompletableFuture<byte[]> generateImageAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateConfiguration();
                
                Map<String, Object> requestBody = createRequestBody(prompt);
                log.info("Making OpenAI API call with request: {}", requestBody.keySet());
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", config.getApiKeyHeader());
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_API_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
                );
                
                log.debug("Received response from OpenAI API: {}", response.getStatusCode());
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                    return extractImageFromResponse(jsonResponse);
                } else {
                    throw new FalAiException("OpenAI API returned status: " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Error calling OpenAI API", e);
                throw new FalAiException("Failed to generate image with OpenAI: " + getDetailedErrorMessage(e), e);
            }
        });
    }
    
    private Map<String, Object> createRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("prompt", prompt);
        
        log.debug("OpenAI request parameters: {}", requestBody);
        return requestBody;
    }
    
    private byte[] extractImageFromResponse(JsonNode response) {
        try {
            JsonNode dataArray = response.path("data");
            if (dataArray.isArray() && dataArray.size() > 0) {
                JsonNode firstImage = dataArray.get(0);
                
                // First try b64_json (which seems to be the default for gpt-image-1)
                String base64Data = firstImage.path("b64_json").asText();
                if (!base64Data.isEmpty()) {
                    log.debug("Found base64 data in OpenAI response");
                    return Base64.getDecoder().decode(base64Data);
                }
                
                // Fallback to URL if available
                String imageUrl = firstImage.path("url").asText();
                if (!imageUrl.isEmpty()) {
                    log.info("Downloading image from OpenAI URL: {}", imageUrl);
                    return downloadImageFromUrl(imageUrl);
                }
            }
            
            log.error("Could not extract image data from OpenAI response: {}", response);
            throw new FalAiException("Invalid response format from OpenAI - no image URL or data found");
            
        } catch (Exception e) {
            log.error("Error extracting image from OpenAI response", e);
            throw new FalAiException("Failed to extract image from OpenAI response: " + e.getMessage(), e);
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
                log.info("Successfully downloaded image from OpenAI: {} bytes", imageData.length);
                return imageData;
                
            }
        } catch (IOException e) {
            log.error("Failed to download image from URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download image from OpenAI URL: " + imageUrl, e);
        }
    }
    
    @Override
    public String getModelName() {
        return "OpenAI " + config.getModel();
    }
    
    @Override
    public boolean isAvailable() {
        try {
            validateConfiguration();
            return true;
        } catch (Exception e) {
            log.warn("OpenAI service is not available: {}", e.getMessage());
            return false;
        }
    }
    
    private void validateConfiguration() {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new FalAiException("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable or openai.api-key property.");
        }
        
        if (config.getApiKey().equals("your-openai-api-key-here")) {
            throw new FalAiException("OpenAI API key is still set to default value. Please provide a valid API key.");
        }
        
        if (config.getModel() == null || config.getModel().trim().isEmpty()) {
            throw new FalAiException("OpenAI model is not configured.");
        }
        
        // Validate API key format (OpenAI keys typically start with sk-)
        if (!isValidApiKeyFormat(config.getApiKey())) {
            throw new FalAiException("OpenAI API key format appears to be invalid. Expected format: sk-...");
        }
        
        log.debug("OpenAI configuration validated successfully. Model: {}, API key format: valid", 
                config.getModel());
    }
    
    private boolean isValidApiKeyFormat(String apiKey) {
        // OpenAI API keys typically start with "sk-" and are longer than 40 characters
        return apiKey.startsWith("sk-") && apiKey.length() > 40;
    }
    
    private String getDetailedErrorMessage(Exception e) {
        String message = e.getMessage();
        
        if (message != null) {
            if (message.contains("401") || message.contains("Unauthorized")) {
                return "Authentication failed (401 Unauthorized). Possible causes:\n" +
                       "1. Invalid API key\n" +
                       "2. API key doesn't have required permissions\n" +
                       "3. API key has expired\n" +
                       "Current API key format: " + getMaskedApiKey() + "\n" +
                       "Please verify your OpenAI API key at https://platform.openai.com/api-keys";
            }
            
            if (message.contains("403") || message.contains("Forbidden")) {
                return "Access forbidden (403). Your API key doesn't have permission to access the DALL-E API.";
            }
            
            if (message.contains("429") || message.contains("Rate limit")) {
                return "Rate limit exceeded (429). Please wait before making more requests.";
            }
            
            if (message.contains("500") || message.contains("Internal Server Error")) {
                return "OpenAI server error (500). Please try again later.";
            }
            
            if (message.contains("400") || message.contains("Bad Request")) {
                return "Bad request (400). Check your input parameters and model configuration.";
            }
        }
        
        return "OpenAI API call failed: " + (message != null ? message : "Unknown error");
    }
    
    private String getMaskedApiKey() {
        if (config.getApiKey() == null || config.getApiKey().length() < 8) {
            return "[INVALID_KEY]";
        }
        
        return config.getApiKey().substring(0, 8) + "***";
    }
    
    /**
     * Test the API connection with a simple request
     */
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateConfiguration();
                
                Map<String, Object> testRequest = new HashMap<>();
                testRequest.put("model", config.getModel());
                testRequest.put("prompt", "test icon");
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", config.getApiKeyHeader());
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(testRequest, headers);
                
                log.info("Testing OpenAI connection with model: {}", config.getModel());
                
                ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_API_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
                );
                
                boolean success = response.getStatusCode() == HttpStatus.OK;
                log.info("OpenAI connection test {}: {}", success ? "successful" : "failed", response.getStatusCode());
                return success;
                
            } catch (Exception e) {
                log.error("OpenAI connection test failed: {}", getDetailedErrorMessage(e), e);
                return false;
            }
        });
    }
}
