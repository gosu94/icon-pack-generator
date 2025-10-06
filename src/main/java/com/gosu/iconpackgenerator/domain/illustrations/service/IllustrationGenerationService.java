package com.gosu.iconpackgenerator.domain.illustrations.service;

import com.gosu.iconpackgenerator.domain.ai.BananaModelService;
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
            progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "banana", 1));
            if (request.getGenerationsPerService() > 1) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "banana", 2));
            }
        }
        
        // Generate multiple generations
        CompletableFuture<List<IllustrationGenerationResponse.ServiceResults>> bananaFuture =
            generateMultipleGenerationsWithBanana(request, requestId, seed, progressCallback);
        
        return bananaFuture.thenApply(bananaResults -> {
            IllustrationGenerationResponse finalResponse = createCombinedResponse(requestId, bananaResults, seed);
            
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
            if (progressCallback != null) {
                progressCallback.onUpdate(ServiceProgressUpdate.allCompleteWithIcons(
                    requestId, finalResponse.getMessage(), convertToIconList(finalResponse.getIllustrations())));
            }
            
            return finalResponse;
        }).exceptionally(error -> {
            log.error("Error generating illustrations for request {}", requestId, error);
            return createErrorResponse(requestId, "Failed to generate illustrations: " + error.getMessage());
        });
    }
    
    /**
     * Generate multiple generations with Banana service
     */
    private CompletableFuture<List<IllustrationGenerationResponse.ServiceResults>> 
            generateMultipleGenerationsWithBanana(
                IllustrationGenerationRequest request, String requestId, 
                Long baseSeed, ProgressUpdateCallback progressCallback) {
        
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
                generateIllustrationsWithBanana(modifiedRequest, requestId, generationSeed)
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
                                    requestId, serviceGenName, convertToIconList(result.getIllustrations()), 
                                    result.getOriginalGridImageBase64(), result.getGenerationTimeMs(), generationIndex));
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
     * Generate illustrations with Banana service
     */
    private CompletableFuture<IllustrationGenerationResponse.ServiceResults> 
            generateIllustrationsWithBanana(
                IllustrationGenerationRequest request, String requestId, Long seed) {
        
        long startTime = System.currentTimeMillis();
        
        return generateIllustrationsInternal(request, seed)
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
                IllustrationGenerationResponse.ServiceResults result = 
                    new IllustrationGenerationResponse.ServiceResults();
                result.setServiceName("banana");
                result.setStatus("error");
                result.setMessage("Failed to generate illustrations: " + error.getMessage());
                result.setIllustrations(new ArrayList<>());
                result.setGenerationTimeMs(generationTime);
                return result;
            });
    }
    
    /**
     * Internal illustration generation logic
     */
    private CompletableFuture<IllustrationGenerationResult> generateIllustrationsInternal(
            IllustrationGenerationRequest request, Long seed) {
        
        if (request.hasReferenceImage()) {
            return generateWithReferenceImage(request, seed);
        } else {
            return generateWithTextPrompt(request, seed);
        }
    }
    
    /**
     * Generate illustrations from text prompt
     */
    private CompletableFuture<IllustrationGenerationResult> generateWithTextPrompt(
            IllustrationGenerationRequest request, Long seed) {
        
        String prompt = illustrationPromptGenerationService.generatePromptFor2x2Grid(
            request.getGeneralDescription(),
            request.getIndividualDescriptions()
        );
        
        return bananaModelService.generateImage(prompt, seed)
            .thenApply(imageData -> {
                List<String> base64Illustrations = 
                    illustrationImageProcessingService.cropIllustrationsFromGrid(imageData);
                return createIllustrationListWithOriginalImage(base64Illustrations, imageData, request);
            });
    }
    
    /**
     * Generate illustrations from reference image
     */
    private CompletableFuture<IllustrationGenerationResult> generateWithReferenceImage(
            IllustrationGenerationRequest request, Long seed) {
        
        String prompt = illustrationPromptGenerationService.generatePromptForReferenceImage(
            request.getIndividualDescriptions(),
            request.getGeneralDescription()
        );
        
        byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());
        
        return bananaModelService.generateImageToImage(prompt, referenceImageData, seed)
            .thenApply(imageData -> {
                List<String> base64Illustrations = 
                    illustrationImageProcessingService.cropIllustrationsFromGrid(imageData);
                return createIllustrationListWithOriginalImage(base64Illustrations, imageData, request);
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
}

