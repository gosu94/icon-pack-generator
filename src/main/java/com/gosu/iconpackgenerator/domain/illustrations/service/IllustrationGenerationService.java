package com.gosu.iconpackgenerator.domain.illustrations.service;

import com.gosu.iconpackgenerator.domain.ai.BananaModelService;
import com.gosu.iconpackgenerator.domain.ai.SeedVrUpscaleService;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
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
public class IllustrationGenerationService {
    
    private final BananaModelService bananaModelService;
    private final SeedVrUpscaleService seedVrUpscaleService;
    private final IllustrationImageProcessingService illustrationImageProcessingService;
    private final IllustrationPromptGenerationService illustrationPromptGenerationService;
    private final CoinManagementService coinManagementService;
    private final IllustrationPersistenceService illustrationPersistenceService;
    private final IllustrationTrialModeService illustrationTrialModeService;
    
    public interface ProgressUpdateCallback {
        void onUpdate(ServiceProgressUpdate update);
    }
    
    public CompletableFuture<IllustrationGenerationResponse> generateIllustrations(
            IllustrationGenerationRequest request, User user) {
        return generateIllustrations(request, UUID.randomUUID().toString(), null, user);
    }
    
    /**
     * Generate illustrations with optional progress callback for real-time updates
     */
    public CompletableFuture<IllustrationGenerationResponse> generateIllustrations(
            IllustrationGenerationRequest request, String requestId, 
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
        
        log.info("Starting illustration generation for {} illustrations with theme: {} (seed: {}, trial mode: {})",
            request.getIllustrationCount(), request.getGeneralDescription(), seed, isTrialMode);
        
        // Send initial progress updates
        if (progressCallback != null) {
            notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "banana", 1), isTrialMode);
            if (request.getGenerationsPerService() > 1) {
                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "banana", 2), isTrialMode);
            }
        }
        
        // Generate multiple generations
        CompletableFuture<List<IllustrationGenerationResponse.ServiceResults>> bananaFuture =
            generateMultipleGenerationsWithBanana(request, requestId, seed, progressCallback, isTrialMode);
        
        return bananaFuture.thenApply(bananaResults -> {
            IllustrationGenerationResponse finalResponse = createCombinedResponse(requestId, bananaResults, seed);
            finalResponse.setTrialMode(isTrialMode);
            
            // Apply trial mode limitations if using trial coins
            if (isTrialMode && "success".equals(finalResponse.getStatus())) {
                log.info("Applying trial mode limitations to illustration response for request {}", requestId);
                illustrationTrialModeService.applyTrialLimitations(finalResponse);
            }
            
            // Persist generated illustrations to database and file system
            if ("success".equals(finalResponse.getStatus())) {
                try {
                    illustrationPersistenceService.persistGeneratedIllustrations(
                            requestId, request, finalResponse, user);
                    log.info("Successfully persisted {} illustrations for request {} (trial mode: {})", 
                            finalResponse.getIllustrations().size(), requestId, isTrialMode);
                } catch (Exception e) {
                    log.error("Error persisting illustrations for request {}", requestId, e);
                    // Don't fail the entire request if persistence fails
                }
            }
            
            // Send final completion update with limited illustrations
            notifyProgressUpdate(progressCallback, ServiceProgressUpdate.allCompleteWithIcons(
                    requestId, finalResponse.getMessage(), convertToIconList(finalResponse.getIllustrations())), isTrialMode);
            
            return finalResponse;
        }).exceptionally(error -> {
            log.error("Error generating illustrations for request {}", requestId, error);
            
            // Refund coins on error
            try {
                coinManagementService.refundCoins(user, cost, isTrialMode);
                log.info("Refunded {} coin(s) to user {} due to illustration generation error",
                        cost, user.getEmail());
            } catch (Exception refundException) {
                log.error("Failed to refund coins to user {}", user.getEmail(), refundException);
            }
            
            // Sanitize error message for user display
            String sanitizedError = sanitizeErrorMessage(error);
            IllustrationGenerationResponse errorResponse = createErrorResponse(requestId, sanitizedError);
            errorResponse.setTrialMode(isTrialMode);
            return errorResponse;
        });
    }
    
    /**
     * Generate multiple generations with Banana service
     */
    private CompletableFuture<List<IllustrationGenerationResponse.ServiceResults>> 
            generateMultipleGenerationsWithBanana(
                IllustrationGenerationRequest request, String requestId, 
                Long baseSeed, ProgressUpdateCallback progressCallback, boolean isTrialMode) {
        
        int generationsCount = request.getGenerationsPerService();
        log.info("Generating {} generations with Banana", generationsCount);
        
        List<CompletableFuture<IllustrationGenerationResponse.ServiceResults>> generationFutures = new ArrayList<>();
        
        for (int i = 0; i < generationsCount; i++) {
            Long generationSeed = baseSeed + i;
            final int generationIndex = i + 1;
            
            // Create modified request for second generation with style variation
            IllustrationGenerationRequest modifiedRequest = request;
            if (generationIndex == 2) {
                modifiedRequest = createStyledVariationRequest(request);
            }
            
            CompletableFuture<IllustrationGenerationResponse.ServiceResults> generationFuture = 
                generateIllustrationsWithBanana(modifiedRequest, requestId, generationSeed, progressCallback, generationIndex)
                    .thenApply(result -> {
                        result.setGenerationIndex(generationIndex);
                        return result;
                    })
                    .whenComplete((result, error) -> {
                        if (progressCallback != null) {
                            String serviceGenName = "banana-gen" + generationIndex;
                            if (error != null) {
                                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceFailed(
                                        requestId, serviceGenName, error.getMessage(),
                                        result != null ? result.getGenerationTimeMs() : 0L, generationIndex), isTrialMode);
                            } else if ("success".equals(result.getStatus())) {
                                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceCompleted(
                                        requestId, serviceGenName, convertToIconList(result.getIllustrations()),
                                        result.getOriginalGridImageBase64(), result.getGenerationTimeMs(), generationIndex), isTrialMode);
                            } else if ("error".equals(result.getStatus())) {
                                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceFailed(
                                        requestId, serviceGenName, result.getMessage(),
                                        result.getGenerationTimeMs(), generationIndex), isTrialMode);
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
     * Generate illustrations with Banana service
     */
    private CompletableFuture<IllustrationGenerationResponse.ServiceResults> 
            generateIllustrationsWithBanana(
                IllustrationGenerationRequest request, String requestId, Long seed,
                ProgressUpdateCallback progressCallback, int generationIndex) {
        
        long startTime = System.currentTimeMillis();
        
        return generateIllustrationsInternal(request, seed, progressCallback, requestId, generationIndex)
            .thenApply(illustrationResult -> {
                long generationTime = System.currentTimeMillis() - startTime;
                IllustrationGenerationResponse.ServiceResults result = 
                    new IllustrationGenerationResponse.ServiceResults();
                result.setServiceName("banana");
                result.setStatus("success");
                result.setMessage("Illustrations generated successfully");
                result.setIllustrations(illustrationResult.getIllustrations());
                result.setOriginalGridImageBase64(illustrationResult.getOriginalGridImageBase64());
                result.setGenerationTimeMs(generationTime);
                return result;
            })
            .exceptionally(error -> {
                long generationTime = System.currentTimeMillis() - startTime;
                log.error("Error generating illustrations with Banana", error);
                
                // Sanitize error message
                String sanitizedError = sanitizeErrorMessage(error);
                
                IllustrationGenerationResponse.ServiceResults result = 
                    new IllustrationGenerationResponse.ServiceResults();
                result.setServiceName("banana");
                result.setStatus("error");
                result.setMessage(sanitizedError);
                result.setIllustrations(new ArrayList<>());
                result.setGenerationTimeMs(generationTime);
                return result;
            });
    }
    
    /**
     * Internal illustration generation logic
     */
    private CompletableFuture<IllustrationGenerationResult> generateIllustrationsInternal(
            IllustrationGenerationRequest request, Long seed, 
            ProgressUpdateCallback progressCallback, String requestId, int generationIndex) {
        
        if (request.hasReferenceImage()) {
            return generateWithReferenceImage(request, seed, progressCallback, requestId, generationIndex);
        } else {
            return generateWithTextPrompt(request, seed, progressCallback, requestId, generationIndex);
        }
    }
    
    /**
     * Generate illustrations from text prompt
     */
    private CompletableFuture<IllustrationGenerationResult> generateWithTextPrompt(
            IllustrationGenerationRequest request, Long seed,
            ProgressUpdateCallback progressCallback, String requestId, int generationIndex) {

        String prompt = illustrationPromptGenerationService.generatePromptFor2x2Grid(
                request.getGeneralDescription(),
                request.getIndividualDescriptions()
        );

        return bananaModelService.generateImage(prompt, seed, "4:3", true)
                .thenCompose(imageData -> {
                    log.info("Upscaling illustration image before processing (factor: 2)");

                    long upscaleStart = System.currentTimeMillis();
                    // Upscale the image by 2x before processing
                    return seedVrUpscaleService.upscaleImage(imageData, 2.0f)
                            .thenApply(upscaledImageData -> {
                                long upscaleDuration = System.currentTimeMillis() - upscaleStart;
                                log.info("Upscaling completed in {}ms, processing upscaled image", upscaleDuration);
                                List<String> base64Illustrations =
                                        illustrationImageProcessingService.cropIllustrationsFromGrid(upscaledImageData, upscaleDuration);
                                return createIllustrationListWithOriginalImage(base64Illustrations, imageData, request);
                            });
                });
    }
    
    /**
     * Generate illustrations from reference image
     */
    private CompletableFuture<IllustrationGenerationResult> generateWithReferenceImage(
            IllustrationGenerationRequest request, Long seed,
            ProgressUpdateCallback progressCallback, String requestId, int generationIndex) {

        String prompt = illustrationPromptGenerationService.generatePromptForReferenceImage(
                request.getIndividualDescriptions(),
                request.getGeneralDescription()
        );

        byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());

        return bananaModelService.generateImageToImage(prompt, referenceImageData, seed, "4:3", true)
                .thenCompose(imageData -> {
                    log.info("Upscaling illustration image before processing (factor: 2)");

                    long upscaleStart = System.currentTimeMillis();
                    // Upscale the image by 2x before processing
                    return seedVrUpscaleService.upscaleImage(imageData, 2.0f)
                            .thenApply(upscaledImageData -> {
                                long upscaleDuration = System.currentTimeMillis() - upscaleStart;
                                log.info("Upscaling completed in {}ms, processing upscaled image", upscaleDuration);
                                List<String> base64Illustrations =
                                        illustrationImageProcessingService.cropIllustrationsFromGrid(upscaledImageData, upscaleDuration);
                                return createIllustrationListWithOriginalImage(base64Illustrations, imageData, request);
                            });
                });
    }
    
    /**
     * Generate more illustrations from an original grid image with upscaling
     * This is used for the "Generate More" functionality
     */
    public CompletableFuture<List<IllustrationGenerationResponse.GeneratedIllustration>> generateMoreIllustrationsFromImage(
            byte[] originalImageData, String prompt, Long seed, List<String> descriptions) {

        return bananaModelService.generateImageToImage(prompt, originalImageData, seed, "4:3", true)
                .thenCompose(imageData -> {
                    log.info("Upscaling more illustrations image before processing (factor: 2)");

                    long upscaleStart = System.currentTimeMillis();
                    // Upscale the image by 2x before processing
                    return seedVrUpscaleService.upscaleImage(imageData, 2.0f)
                            .thenApply(upscaledImageData -> {
                                long upscaleDuration = System.currentTimeMillis() - upscaleStart;
                                log.info("Upscaling completed, processing upscaled more illustrations in {}ms", upscaleDuration);
                                List<String> base64Illustrations =
                                        illustrationImageProcessingService.cropIllustrationsFromGrid(upscaledImageData, upscaleDuration);

                                // Convert to GeneratedIllustration objects
                                List<IllustrationGenerationResponse.GeneratedIllustration> illustrations = new ArrayList<>();
                                for (int i = 0; i < base64Illustrations.size() && i < 4; i++) {
                                    IllustrationGenerationResponse.GeneratedIllustration illustration =
                                            new IllustrationGenerationResponse.GeneratedIllustration();
                                    illustration.setId(UUID.randomUUID().toString());
                                    illustration.setBase64Data(base64Illustrations.get(i));
                                    illustration.setServiceSource("banana");
                                    illustration.setGridPosition(i);

                                    if (descriptions != null && i < descriptions.size()) {
                                        illustration.setDescription(descriptions.get(i));
                                    } else {
                                        illustration.setDescription("");
                                    }

                                    illustrations.add(illustration);
                                }

                                return illustrations;
                            });
                });
    }
    
    /**
     * Helper class to hold illustrations and original image data
     */
    private static class IllustrationGenerationResult {
        private final List<IllustrationGenerationResponse.GeneratedIllustration> illustrations;
        private final String originalGridImageBase64;
        
        public IllustrationGenerationResult(
                List<IllustrationGenerationResponse.GeneratedIllustration> illustrations, 
                String originalGridImageBase64) {
            this.illustrations = illustrations;
            this.originalGridImageBase64 = originalGridImageBase64;
        }
        
        public List<IllustrationGenerationResponse.GeneratedIllustration> getIllustrations() {
            return illustrations;
        }
        
        public String getOriginalGridImageBase64() {
            return originalGridImageBase64;
        }
    }
    
    /**
     * Create illustration list with original grid image
     */
    private IllustrationGenerationResult createIllustrationListWithOriginalImage(
            List<String> base64Illustrations, byte[] originalImageData, 
            IllustrationGenerationRequest request) {
        
        List<IllustrationGenerationResponse.GeneratedIllustration> illustrations = new ArrayList<>();
        
        for (int i = 0; i < base64Illustrations.size(); i++) {
            IllustrationGenerationResponse.GeneratedIllustration illustration = 
                new IllustrationGenerationResponse.GeneratedIllustration();
            illustration.setId(UUID.randomUUID().toString());
            illustration.setBase64Data(base64Illustrations.get(i));
            illustration.setDescription("");
            illustration.setGridPosition(i);
            illustration.setServiceSource("banana");
            illustrations.add(illustration);
        }
        
        String originalGridImageBase64 = Base64.getEncoder().encodeToString(originalImageData);
        return new IllustrationGenerationResult(illustrations, originalGridImageBase64);
    }
    
    /**
     * Create combined response
     */
    private IllustrationGenerationResponse createCombinedResponse(
            String requestId, List<IllustrationGenerationResponse.ServiceResults> bananaResults, Long seed) {
        
        IllustrationGenerationResponse response = new IllustrationGenerationResponse();
        response.setRequestId(requestId);
        response.setBananaResults(bananaResults);
        response.setSeed(seed);
        
        // Combine all illustrations
        List<IllustrationGenerationResponse.GeneratedIllustration> allIllustrations = new ArrayList<>();
        for (IllustrationGenerationResponse.ServiceResults result : bananaResults) {
            if (result.getIllustrations() != null) {
                allIllustrations.addAll(result.getIllustrations());
            }
        }
        response.setIllustrations(allIllustrations);
        
        // Set overall status
        int successCount = 0;
        for (IllustrationGenerationResponse.ServiceResults result : bananaResults) {
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
            response.setMessage("Failed to generate illustrations");
        }
        
        return response;
    }
    
    /**
     * Create error response
     */
    private IllustrationGenerationResponse createErrorResponse(String requestId, String message) {
        IllustrationGenerationResponse response = new IllustrationGenerationResponse();
        response.setRequestId(requestId);
        response.setStatus("error");
        response.setMessage(message);
        response.setIllustrations(new ArrayList<>());
        
        IllustrationGenerationResponse.ServiceResults errorResult = 
            new IllustrationGenerationResponse.ServiceResults();
        errorResult.setStatus("error");
        errorResult.setMessage(message);
        errorResult.setIllustrations(new ArrayList<>());
        errorResult.setGenerationIndex(1);
        
        response.setBananaResults(List.of(errorResult));
        
        return response;
    }
    
    /**
     * Create styled variation request
     */
    private IllustrationGenerationRequest createStyledVariationRequest(
            IllustrationGenerationRequest originalRequest) {
        
        IllustrationGenerationRequest modifiedRequest = new IllustrationGenerationRequest();
        modifiedRequest.setIllustrationCount(originalRequest.getIllustrationCount());
        modifiedRequest.setIndividualDescriptions(originalRequest.getIndividualDescriptions());
        modifiedRequest.setSeed(originalRequest.getSeed());
        modifiedRequest.setGenerationsPerService(originalRequest.getGenerationsPerService());
        
        String originalDescription = originalRequest.getGeneralDescription();
        String styledDescription = originalDescription + 
            IllustrationPromptGenerationService.SECOND_GENERATION_VARIATION;
        modifiedRequest.setGeneralDescription(styledDescription);
        
        if (originalRequest.hasReferenceImage()) {
            modifiedRequest.setReferenceImageBase64(originalRequest.getReferenceImageBase64());
        }
        
        return modifiedRequest;
    }
    
    /**
     * Convert illustrations to icon list format for progress updates
     */
    private List<com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse.GeneratedIcon> 
            convertToIconList(List<IllustrationGenerationResponse.GeneratedIllustration> illustrations) {
        
        if (illustrations == null) {
            return new ArrayList<>();
        }
        
        List<com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse.GeneratedIcon> icons = 
            new ArrayList<>();
        
        for (IllustrationGenerationResponse.GeneratedIllustration illustration : illustrations) {
            com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse.GeneratedIcon icon = 
                new com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse.GeneratedIcon();
            icon.setId(illustration.getId());
            icon.setBase64Data(illustration.getBase64Data());
            icon.setDescription(illustration.getDescription());
            icon.setGridPosition(illustration.getGridPosition());
            icon.setServiceSource(illustration.getServiceSource());
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
     * Sanitize error messages for user display, especially for content policy violations
     */
    private String sanitizeErrorMessage(Throwable error) {
        String errorMessage = error.getMessage() != null ? error.getMessage() : error.toString();
        
        // Check for HTTP error codes indicating content policy violations
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
            // Extract just the meaningful part after the exception type
            int colonIndex = errorMessage.lastIndexOf(":");
            if (colonIndex > 0 && colonIndex < errorMessage.length() - 1) {
                String extractedMessage = errorMessage.substring(colonIndex + 1).trim();
                
                // Check if extracted message mentions content policy
                if (extractedMessage.toLowerCase().contains("policy") || 
                    extractedMessage.toLowerCase().contains("content") ||
                    extractedMessage.toLowerCase().contains("unsafe")) {
                    return "Request rejected due to content policy. Please ensure your descriptions comply with AI service guidelines.";
                }
                
                // Return sanitized extracted message
                return "Generation failed: " + extractedMessage;
            }
            return "Generation failed due to AI service error. Please try again.";
        }
        
        // Default case: return a generic error message without technical details
        return "Failed to generate illustrations. Please try again or contact support if the issue persists.";
    }

    private void notifyProgressUpdate(ProgressUpdateCallback progressCallback,
                                      ServiceProgressUpdate update,
                                      boolean isTrialMode) {
        if (progressCallback == null || update == null) {
            return;
        }
        update.setTrialMode(isTrialMode);
        progressCallback.onUpdate(update);
    }
}
