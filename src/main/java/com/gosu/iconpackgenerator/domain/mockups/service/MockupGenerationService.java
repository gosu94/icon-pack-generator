package com.gosu.iconpackgenerator.domain.mockups.service;

import com.gosu.iconpackgenerator.domain.ai.BananaModelService;
import com.gosu.iconpackgenerator.domain.ai.SeedVrUpscaleService;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse;
import com.gosu.iconpackgenerator.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockupGenerationService {
    
    private final BananaModelService bananaModelService;
    private final SeedVrUpscaleService seedVrUpscaleService;
    private final MockupImageProcessingService mockupImageProcessingService;
    private final MockupPromptGenerationService mockupPromptGenerationService;
    private final CoinManagementService coinManagementService;
    private final MockupPersistenceService mockupPersistenceService;
    
    private static final String ASPECT_RATIO_16_9 = "16:9";
    
    public interface ProgressUpdateCallback {
        void onUpdate(ServiceProgressUpdate update);
    }
    
    public CompletableFuture<MockupGenerationResponse> generateMockups(
            MockupGenerationRequest request, User user) {
        return generateMockups(request, UUID.randomUUID().toString(), null, user);
    }
    
    /**
     * Generate mockups with optional progress callback for real-time updates
     */
    public CompletableFuture<MockupGenerationResponse> generateMockups(
            MockupGenerationRequest request, String requestId, 
            ProgressUpdateCallback progressCallback, User user) {
        
        int cost = Math.max(1, request.getGenerationsPerService());
        
        // Deduct coins
        CoinManagementService.CoinDeductionResult coinResult = 
            coinManagementService.deductCoinsForGeneration(user, cost);
        
        if (!coinResult.isSuccess()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, coinResult.getErrorMessage()));
        }
        
        final boolean isTrialMode = coinResult.isUsedTrialCoins();
        
        // Generate or use provided seed
        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();
        
        log.info("Starting mockup generation with description: {} (seed: {}, trial mode: {})",
            request.getDescription(), seed, isTrialMode);
        
        // Send initial progress updates
        if (progressCallback != null) {
            progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "banana", 1));
            if (request.getGenerationsPerService() > 1) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "banana", 2));
            }
        }
        
        // Generate multiple generations
        CompletableFuture<List<MockupGenerationResponse.ServiceResults>> bananaFuture =
            generateMultipleGenerationsWithBanana(request, requestId, seed, progressCallback);
        
        return bananaFuture.thenApply(bananaResults -> {
            MockupGenerationResponse finalResponse = createCombinedResponse(requestId, bananaResults, seed);
            
            // Note: Trial mode limitations can be added here if needed
            
            // Persist generated mockups to database and file system
            if ("success".equals(finalResponse.getStatus())) {
                try {
                    mockupPersistenceService.persistGeneratedMockups(
                            requestId, request, finalResponse, user);
                    log.info("Successfully persisted {} mockups for request {} (trial mode: {})", 
                            finalResponse.getMockups().size(), requestId, isTrialMode);
                } catch (Exception e) {
                    log.error("Error persisting mockups for request {}", requestId, e);
                    // Don't fail the entire request if persistence fails
                }
            }
            
            // Send final completion update
            if (progressCallback != null) {
                progressCallback.onUpdate(ServiceProgressUpdate.allCompleteWithIcons(
                    requestId, finalResponse.getMessage(), convertToIconList(finalResponse.getMockups())));
            }
            
            return finalResponse;
        }).exceptionally(error -> {
            log.error("Error generating mockups for request {}", requestId, error);
            
            // Refund coins on error
            try {
                coinManagementService.refundCoins(user, cost, isTrialMode);
                log.info("Refunded {} coin(s) to user {} due to mockup generation error",
                        cost, user.getEmail());
            } catch (Exception refundException) {
                log.error("Failed to refund coins to user {}", user.getEmail(), refundException);
            }
            
            // Sanitize error message for user display
            String sanitizedError = sanitizeErrorMessage(error);
            return createErrorResponse(requestId, sanitizedError);
        });
    }
    
    /**
     * Generate multiple generations with Banana service
     */
    private CompletableFuture<List<MockupGenerationResponse.ServiceResults>> 
            generateMultipleGenerationsWithBanana(
                MockupGenerationRequest request, String requestId, 
                Long baseSeed, ProgressUpdateCallback progressCallback) {
        
        int generationsCount = request.getGenerationsPerService();
        log.info("Generating {} generations with Banana (16:9 aspect ratio)", generationsCount);
        
        List<CompletableFuture<MockupGenerationResponse.ServiceResults>> generationFutures = new ArrayList<>();
        
        for (int i = 0; i < generationsCount; i++) {
            Long generationSeed = baseSeed + i;
            final int generationIndex = i + 1;
            
            // Create modified request for second generation with style variation
            MockupGenerationRequest modifiedRequest = request;
            if (generationIndex == 2) {
                modifiedRequest = createStyledVariationRequest(request);
            }
            
            CompletableFuture<MockupGenerationResponse.ServiceResults> generationFuture = 
                generateMockupsWithBanana(modifiedRequest, requestId, generationSeed, progressCallback, generationIndex)
                    .thenApply(result -> {
                        result.setGenerationIndex(generationIndex);
                        return result;
                    })
                    .whenComplete((result, error) -> {
                        if (progressCallback != null) {
                            String serviceGenName = "banana-gen" + generationIndex;
                            if (error != null) {
                                progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                                    requestId, serviceGenName, error.getMessage(), 
                                    result != null ? result.getGenerationTimeMs() : 0L, generationIndex));
                            } else if ("success".equals(result.getStatus())) {
                                progressCallback.onUpdate(ServiceProgressUpdate.serviceCompleted(
                                    requestId, serviceGenName, convertToIconList(result.getMockups()), 
                                    result.getOriginalImageBase64(), result.getGenerationTimeMs(), generationIndex));
                            } else if ("error".equals(result.getStatus())) {
                                progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                                    requestId, serviceGenName, result.getMessage(), 
                                    result.getGenerationTimeMs(), generationIndex));
                            }
                        }
                    });
            
            generationFutures.add(generationFuture);
        }
        
        return CompletableFuture.allOf(generationFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> generationFutures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    /**
     * Generate mockups with Banana service using 16:9 aspect ratio
     */
    private CompletableFuture<MockupGenerationResponse.ServiceResults> 
            generateMockupsWithBanana(
                MockupGenerationRequest request, String requestId, Long seed,
                ProgressUpdateCallback progressCallback, int generationIndex) {
        
        long startTime = System.currentTimeMillis();
        
        return generateMockupsInternal(request, seed, progressCallback, requestId, generationIndex)
            .thenApply(mockupResult -> {
                long generationTime = System.currentTimeMillis() - startTime;
                MockupGenerationResponse.ServiceResults result = 
                    new MockupGenerationResponse.ServiceResults();
                result.setServiceName("banana");
                result.setStatus("success");
                result.setMessage("Mockups generated successfully");
                result.setMockups(mockupResult.getMockups());
                result.setOriginalImageBase64(mockupResult.getOriginalImageBase64());
                result.setGenerationTimeMs(generationTime);
                return result;
            })
            .exceptionally(error -> {
                long generationTime = System.currentTimeMillis() - startTime;
                log.error("Error generating mockups with Banana", error);
                
                // Sanitize error message
                String sanitizedError = sanitizeErrorMessage(error);
                
                MockupGenerationResponse.ServiceResults result = 
                    new MockupGenerationResponse.ServiceResults();
                result.setServiceName("banana");
                result.setStatus("error");
                result.setMessage(sanitizedError);
                result.setMockups(new ArrayList<>());
                result.setGenerationTimeMs(generationTime);
                return result;
            });
    }
    
    /**
     * Internal mockup generation logic
     */
    private CompletableFuture<MockupGenerationResult> generateMockupsInternal(
            MockupGenerationRequest request, Long seed, 
            ProgressUpdateCallback progressCallback, String requestId, int generationIndex) {
        
        if (request.hasReferenceImage()) {
            return generateWithReferenceImage(request, seed, progressCallback, requestId, generationIndex);
        } else {
            return generateWithTextPrompt(request, seed, progressCallback, requestId, generationIndex);
        }
    }
    
    /**
     * Generate mockups from text prompt
     */
    private CompletableFuture<MockupGenerationResult> generateWithTextPrompt(
            MockupGenerationRequest request, Long seed,
            ProgressUpdateCallback progressCallback, String requestId, int generationIndex) {

        String prompt = mockupPromptGenerationService.generatePromptForMockup(request.getDescription());

        return bananaModelService.generateImage(prompt, seed, ASPECT_RATIO_16_9)
                .thenCompose(imageData -> {
                    log.info("Upscaling mockup image before processing (factor: 2)");

                    long upscaleStart = System.currentTimeMillis();
                    // Upscale the image by 2x
                    return seedVrUpscaleService.upscaleImage(imageData, 2.0f)
                            .thenApply(upscaledImageData -> {
                                long upscaleDuration = System.currentTimeMillis() - upscaleStart;
                                log.info("Upscaling completed in {}ms, processing upscaled image", upscaleDuration);
                                String base64Mockup = mockupImageProcessingService.processMockupImage(upscaledImageData);
                                return createMockupWithOriginalImage(base64Mockup, imageData, request);
                            });
                });
    }
    
    /**
     * Generate mockups from reference image
     */
    private CompletableFuture<MockupGenerationResult> generateWithReferenceImage(
            MockupGenerationRequest request, Long seed,
            ProgressUpdateCallback progressCallback, String requestId, int generationIndex) {

        String prompt = mockupPromptGenerationService.generatePromptForReferenceImage(request.getDescription());

        byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());

        return bananaModelService.generateImageToImage(prompt, referenceImageData, seed, ASPECT_RATIO_16_9)
                .thenCompose(imageData -> {
                    log.info("Upscaling mockup image before processing (factor: 2)");

                    long upscaleStart = System.currentTimeMillis();
                    // Upscale the image by 2x
                    return seedVrUpscaleService.upscaleImage(imageData, 2.0f)
                            .thenApply(upscaledImageData -> {
                                long upscaleDuration = System.currentTimeMillis() - upscaleStart;
                                log.info("Upscaling completed, processing upscaled mockup in {}ms", upscaleDuration);
                                String base64Mockup = mockupImageProcessingService.processMockupImage(upscaledImageData);
                                return createMockupWithOriginalImage(base64Mockup, imageData, request);
                            });
                });
    }
    
    /**
     * Generate more mockups from an original image with upscaling
     * This is used for the "Generate More" functionality
     */
    public CompletableFuture<List<MockupGenerationResponse.GeneratedMockup>> generateMoreMockupsFromImage(
            byte[] originalImageData, String prompt, Long seed) {

        return bananaModelService.generateImageToImage(prompt, originalImageData, seed, ASPECT_RATIO_16_9)
                .thenCompose(imageData -> {
                    log.info("Upscaling more mockups image before processing (factor: 2)");

                    long upscaleStart = System.currentTimeMillis();
                    // Upscale the image by 2x
                    return seedVrUpscaleService.upscaleImage(imageData, 2.0f)
                            .thenApply(upscaledImageData -> {
                                long upscaleDuration = System.currentTimeMillis() - upscaleStart;
                                log.info("Upscaling completed, processing upscaled more mockups in {}ms", upscaleDuration);
                                String base64Mockup = mockupImageProcessingService.processMockupImage(upscaledImageData);

                                // Create list with single mockup
                                List<MockupGenerationResponse.GeneratedMockup> mockups = new ArrayList<>();
                                MockupGenerationResponse.GeneratedMockup mockup =
                                        new MockupGenerationResponse.GeneratedMockup();
                                mockup.setId(UUID.randomUUID().toString());
                                mockup.setBase64Data(base64Mockup);
                                mockup.setServiceSource("banana");
                                mockup.setDescription("");
                                mockups.add(mockup);

                                return mockups;
                            });
                });
    }
    
    /**
     * Helper class to hold mockups and original image data
     */
    private static class MockupGenerationResult {
        private final List<MockupGenerationResponse.GeneratedMockup> mockups;
        private final String originalImageBase64;
        
        public MockupGenerationResult(
                List<MockupGenerationResponse.GeneratedMockup> mockups, 
                String originalImageBase64) {
            this.mockups = mockups;
            this.originalImageBase64 = originalImageBase64;
        }
        
        public List<MockupGenerationResponse.GeneratedMockup> getMockups() {
            return mockups;
        }
        
        public String getOriginalImageBase64() {
            return originalImageBase64;
        }
    }
    
    /**
     * Create mockup with original image
     */
    private MockupGenerationResult createMockupWithOriginalImage(
            String base64Mockup, byte[] originalImageData, 
            MockupGenerationRequest request) {
        
        List<MockupGenerationResponse.GeneratedMockup> mockups = new ArrayList<>();
        
        MockupGenerationResponse.GeneratedMockup mockup = 
            new MockupGenerationResponse.GeneratedMockup();
        mockup.setId(UUID.randomUUID().toString());
        mockup.setBase64Data(base64Mockup);
        mockup.setDescription(request.getDescription());
        mockup.setServiceSource("banana");
        mockups.add(mockup);
        
        String originalImageBase64 = Base64.getEncoder().encodeToString(originalImageData);
        return new MockupGenerationResult(mockups, originalImageBase64);
    }
    
    /**
     * Create combined response
     */
    private MockupGenerationResponse createCombinedResponse(
            String requestId, List<MockupGenerationResponse.ServiceResults> bananaResults, Long seed) {
        
        MockupGenerationResponse response = new MockupGenerationResponse();
        response.setRequestId(requestId);
        response.setBananaResults(bananaResults);
        response.setSeed(seed);
        
        // Combine all mockups
        List<MockupGenerationResponse.GeneratedMockup> allMockups = new ArrayList<>();
        for (MockupGenerationResponse.ServiceResults result : bananaResults) {
            if (result.getMockups() != null) {
                allMockups.addAll(result.getMockups());
            }
        }
        response.setMockups(allMockups);
        
        // Set overall status
        int successCount = 0;
        for (MockupGenerationResponse.ServiceResults result : bananaResults) {
            if ("success".equals(result.getStatus())) {
                successCount++;
            }
        }
        
        if (successCount > 0) {
            response.setStatus("success");
            if (successCount == bananaResults.size()) {
                response.setMessage("All generations completed successfully");
            } else {
                response.setMessage(String.format("Generated %d successful generation(s)", successCount));
            }
        } else {
            response.setStatus("error");
            response.setMessage("Failed to generate mockups");
        }
        
        return response;
    }
    
    /**
     * Create error response
     */
    private MockupGenerationResponse createErrorResponse(String requestId, String message) {
        MockupGenerationResponse response = new MockupGenerationResponse();
        response.setRequestId(requestId);
        response.setStatus("error");
        response.setMessage(message);
        response.setMockups(new ArrayList<>());
        
        MockupGenerationResponse.ServiceResults errorResult = 
            new MockupGenerationResponse.ServiceResults();
        errorResult.setStatus("error");
        errorResult.setMessage(message);
        errorResult.setMockups(new ArrayList<>());
        errorResult.setGenerationIndex(1);
        
        response.setBananaResults(List.of(errorResult));
        
        return response;
    }
    
    /**
     * Create styled variation request
     */
    private MockupGenerationRequest createStyledVariationRequest(
            MockupGenerationRequest originalRequest) {
        
        MockupGenerationRequest modifiedRequest = new MockupGenerationRequest();
        modifiedRequest.setMockupCount(originalRequest.getMockupCount());
        modifiedRequest.setSeed(originalRequest.getSeed());
        modifiedRequest.setGenerationsPerService(originalRequest.getGenerationsPerService());
        
        String originalDescription = originalRequest.getDescription();
        String styledDescription = originalDescription + 
            MockupPromptGenerationService.SECOND_GENERATION_VARIATION;
        modifiedRequest.setDescription(styledDescription);
        
        if (originalRequest.hasReferenceImage()) {
            modifiedRequest.setReferenceImageBase64(originalRequest.getReferenceImageBase64());
        }
        
        return modifiedRequest;
    }
    
    /**
     * Convert mockups to icon list format for progress updates
     */
    private List<com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse.GeneratedIcon> 
            convertToIconList(List<MockupGenerationResponse.GeneratedMockup> mockups) {
        
        if (mockups == null) {
            return new ArrayList<>();
        }
        
        List<com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse.GeneratedIcon> icons = 
            new ArrayList<>();
        
        for (MockupGenerationResponse.GeneratedMockup mockup : mockups) {
            com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse.GeneratedIcon icon = 
                new com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse.GeneratedIcon();
            icon.setId(mockup.getId());
            icon.setBase64Data(mockup.getBase64Data());
            icon.setDescription(mockup.getDescription());
            icon.setGridPosition(0); // Mockups don't have grid positions
            icon.setServiceSource(mockup.getServiceSource());
            icons.add(icon);
        }
        
        return icons;
    }
    
    /**
     * Generate a random seed for reproducible results
     */
    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }
    
    /**
     * Sanitize error messages for user display
     */
    private String sanitizeErrorMessage(Throwable error) {
        String errorMessage = error.getMessage() != null ? error.getMessage() : error.toString();
        
        // Check for HTTP error codes
        if (errorMessage.contains("413") || errorMessage.toLowerCase().contains("request entity too large")) {
            return "Request failed due to content size limits. Please try with a simpler description or smaller reference image.";
        }
        
        if (errorMessage.contains("400") && (errorMessage.toLowerCase().contains("policy") || 
                errorMessage.toLowerCase().contains("content") || 
                errorMessage.toLowerCase().contains("unsafe"))) {
            return "Request rejected due to content policy. Please ensure your descriptions comply with AI service content guidelines.";
        }
        
        if (errorMessage.contains("403") || errorMessage.toLowerCase().contains("forbidden")) {
            return "Request rejected by AI service. Please try again with different content.";
        }
        
        if (errorMessage.contains("429") || errorMessage.toLowerCase().contains("rate limit")) {
            return "Service is temporarily busy. Please try again in a few moments.";
        }
        
        // Generic FalAi errors
        if (errorMessage.contains("FalAiException")) {
            int colonIndex = errorMessage.lastIndexOf(":");
            if (colonIndex > 0 && colonIndex < errorMessage.length() - 1) {
                String extractedMessage = errorMessage.substring(colonIndex + 1).trim();
                
                if (extractedMessage.toLowerCase().contains("policy") || 
                    extractedMessage.toLowerCase().contains("content") ||
                    extractedMessage.toLowerCase().contains("unsafe")) {
                    return "Request rejected due to content policy. Please ensure your descriptions comply with AI service guidelines.";
                }
                
                return "Generation failed: " + extractedMessage;
            }
            return "Generation failed due to AI service error. Please try again.";
        }
        
        // Default case
        return "Failed to generate mockups. Please try again or contact support if the issue persists.";
    }
}

