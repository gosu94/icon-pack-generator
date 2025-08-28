package com.gosu.iconpackgenerator.domain.service;

import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.dto.IconGenerationRequest;
import com.gosu.iconpackgenerator.domain.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.exception.FalAiException;
import com.gosu.iconpackgenerator.domain.repository.GeneratedIconRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.gosu.iconpackgenerator.domain.service.PromptGenerationService.SECOND_GENERATION_VARIATION;

@Service
@RequiredArgsConstructor
@Slf4j
public class IconGenerationService {
    
    private final FluxModelService fluxModelService;
    private final RecraftModelService recraftModelService;
    private final PhotonModelService photonModelService;
    private final GptModelService gptModelService;
    private final ImagenModelService imagenModelService;
    private final BananaModelService bananaModelService;
    private final ImageProcessingService imageProcessingService;
    private final PromptGenerationService promptGenerationService;
    private final AIServicesConfig aiServicesConfig;
    private final GeneratedIconRepository generatedIconRepository;
    private final DataInitializationService dataInitializationService;
    private final FileStorageService fileStorageService;
    
    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request) {
        return generateIcons(request, UUID.randomUUID().toString(), null);
    }

    /**
     * Generate icons with optional progress callback for real-time updates
     */
    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request, String requestId, ProgressUpdateCallback progressCallback) {
        List<String> enabledServices = new ArrayList<>();
        if (aiServicesConfig.isGptEnabled()) enabledServices.add("GPT");
        if (aiServicesConfig.isBananaEnabled()) enabledServices.add("Banana");
        
        // Generate or use provided seed for consistent results across services
        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();
        
        log.info("Starting icon generation for {} icons with theme: {} using enabled services: {} (seed: {})", 
                request.getIconCount(), request.getGeneralDescription(), enabledServices, seed);
        
        // Send initial progress updates for two-model approach
        if (progressCallback != null) {
            if (aiServicesConfig.isGptEnabled()) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "gpt", 1));
            }
            
            if (aiServicesConfig.isBananaEnabled()) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "banana", 2));
            }
        }
        
        // Generate single generation for each enabled service (GPT and Banana)
        CompletableFuture<List<IconGenerationResponse.ServiceResults>> gptFuture = 
                aiServicesConfig.isGptEnabled() ? 
                generateSingleGenerationWithService(request, requestId, gptModelService, "gpt", seed, progressCallback) :
                CompletableFuture.completedFuture(List.of(createDisabledServiceResult("gpt")));
        
        CompletableFuture<List<IconGenerationResponse.ServiceResults>> bananaFuture = 
                aiServicesConfig.isBananaEnabled() ? 
                generateSingleGenerationWithService(request, requestId, bananaModelService, "banana", seed, progressCallback) :
                CompletableFuture.completedFuture(List.of(createDisabledServiceResult("banana")));
        
        return CompletableFuture.allOf(gptFuture, bananaFuture)
                .thenApply(v -> {
                    List<IconGenerationResponse.ServiceResults> gptResults = gptFuture.join();
                    List<IconGenerationResponse.ServiceResults> bananaResults = bananaFuture.join();
                    
                    IconGenerationResponse finalResponse = createTwoModelResponse(requestId, gptResults, bananaResults, seed);
                    
                    // Persist generated icons to database and file system
                    if ("success".equals(finalResponse.getStatus())) {
                        try {
                            persistGeneratedIcons(requestId, request, finalResponse);
                            log.info("Successfully persisted {} icons for request {}", finalResponse.getIcons().size(), requestId);
                        } catch (Exception e) {
                            log.error("Error persisting icons for request {}", requestId, e);
                            // Don't fail the entire request if persistence fails
                        }
                    }
                    
                    // Send final completion update
                    if (progressCallback != null) {
                        progressCallback.onUpdate(ServiceProgressUpdate.allComplete(requestId, finalResponse.getMessage()));
                    }
                    
                    return finalResponse;
                })
                .exceptionally(error -> {
                    log.error("Error generating icons for request {}", requestId, error);
                    return createErrorResponse(requestId, "Failed to generate icons: " + error.getMessage());
                });
    }
    
    /**
     * Generate single generation for a service (used in two-model approach)
     */
    private CompletableFuture<List<IconGenerationResponse.ServiceResults>> generateSingleGenerationWithService(
            IconGenerationRequest request, String requestId, AIModelService aiService, String serviceName, Long seed, ProgressUpdateCallback progressCallback) {
        
        log.info("Generating single generation for service: {}", serviceName);
        
        CompletableFuture<IconGenerationResponse.ServiceResults> generationFuture = generateIconsWithService(request, requestId, aiService, serviceName, seed)
                .thenApply(result -> {
                    // GPT in pane 1 ("your icons"), Banana in pane 2 ("variations")
                    int generationIndex = "banana".equals(serviceName) ? 2 : 1;
                    result.setGenerationIndex(generationIndex);
                    return result;
                })
                .whenComplete((result, error) -> {
                    if (progressCallback != null) {
                        // Use correct generation index for progress updates
                        int generationIndex = "banana".equals(serviceName) ? 2 : 1;
                        String serviceGenName = serviceName + "-gen" + generationIndex;
                        if (error != null) {
                            progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                                    requestId, serviceGenName, getDetailedErrorMessage(error, serviceName), result != null ? result.getGenerationTimeMs() : 0L, generationIndex));
                        } else if ("success".equals(result.getStatus())) {
                            progressCallback.onUpdate(ServiceProgressUpdate.serviceCompleted(
                                    requestId, serviceGenName, result.getIcons(), result.getOriginalGridImageBase64(), result.getGenerationTimeMs(), generationIndex));
                        } else if ("error".equals(result.getStatus())) {
                            progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                                    requestId, serviceGenName, result.getMessage(), result.getGenerationTimeMs(), generationIndex));
                        }
                    }
                });
        
        return generationFuture.thenApply(result -> List.of(result));
    }
    
    private CompletableFuture<IconGenerationResponse.ServiceResults> generateIconsWithService(
            IconGenerationRequest request, String requestId, AIModelService aiService, String serviceName, Long seed) {
        
        long startTime = System.currentTimeMillis();
        
        return generateIconsInternalWithService(request, aiService, serviceName, seed)
                .thenApply(iconResult -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    IconGenerationResponse.ServiceResults result = new IconGenerationResponse.ServiceResults();
                    result.setServiceName(serviceName);
                    result.setStatus("success");
                    result.setMessage("Icons generated successfully");
                    result.setIcons(iconResult.getIcons());
                    result.setOriginalGridImageBase64(iconResult.getOriginalGridImageBase64());
                    result.setGenerationTimeMs(generationTime);
                    return result;
                })
                .exceptionally(error -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    log.error("Error generating icons with {}", serviceName, error);
                    IconGenerationResponse.ServiceResults result = new IconGenerationResponse.ServiceResults();
                    result.setServiceName(serviceName);
                    result.setStatus("error");
                    result.setMessage(getDetailedErrorMessage(error, serviceName));
                    result.setIcons(new ArrayList<>());
                    result.setGenerationTimeMs(generationTime);
                    return result;
                });
    }
    
    private CompletableFuture<IconGenerationResult> generateIconsInternalWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        if (request.getIconCount() == 9) {
            return generateSingleGridWithService(request, aiService, serviceName, seed);
        } else {
            return generateDoubleGridWithService(request, aiService, serviceName, seed);
        }
    }
    
    private CompletableFuture<IconGenerationResult> generateSingleGridWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        
        // Check if this is a reference image-based request
        if (request.hasReferenceImage()) {
            return generateSingleGridWithReferenceImage(request, aiService, serviceName, seed);
        } else {
            return generateSingleGridWithTextPrompt(request, aiService, serviceName, seed);
        }
    }
    
    private CompletableFuture<IconGenerationResult> generateSingleGridWithTextPrompt(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        String prompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(), 
                request.getIndividualDescriptions()
        );
        
        return generateImageWithSeed(aiService, prompt, seed)
                .thenApply(imageData -> {
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false );
                    return createIconListWithOriginalImage(base64Icons, imageData, request, serviceName);
                });
    }
    
    private CompletableFuture<IconGenerationResult> generateSingleGridWithReferenceImage(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        String prompt = promptGenerationService.generatePromptForReferenceImage(
                request.getIndividualDescriptions(),
                request.getGeneralDescription()
        );
        
        // Convert base64 reference image to byte array
        byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());
        
        // Use image-to-image generation with the reference image
        return generateImageToImageWithService(aiService, prompt, referenceImageData, seed)
                .thenApply(imageData -> {
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, true);
                    return createIconListWithOriginalImage(base64Icons, imageData, request, serviceName);
                });
    }
    
    private CompletableFuture<IconGenerationResult> generateDoubleGridWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        
        // Check if this is a reference image-based request
        if (request.hasReferenceImage()) {
            return generateDoubleGridWithReferenceImage(request, aiService, serviceName, seed);
        } else {
            return generateDoubleGridWithTextPrompt(request, aiService, serviceName, seed);
        }
    }
    
    private CompletableFuture<IconGenerationResult> generateDoubleGridWithTextPrompt(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        // For 18 icons, generate first grid normally, then use image-to-image for second grid
        List<String> firstNineDescriptions = request.getIndividualDescriptions() != null ? 
                request.getIndividualDescriptions().subList(0, Math.min(9, request.getIndividualDescriptions().size())) : 
                new ArrayList<>();
        
        List<String> secondNineDescriptions = request.getIndividualDescriptions() != null && 
                request.getIndividualDescriptions().size() > 9 ? 
                request.getIndividualDescriptions().subList(9, Math.min(18, request.getIndividualDescriptions().size())) : 
                new ArrayList<>();
        
        String firstPrompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(), firstNineDescriptions);
        
        // Generate first grid
        return generateImageWithSeed(aiService, firstPrompt, seed)
                .thenCompose(firstImageData -> {
                    List<String> firstGrid = imageProcessingService.cropIconsFromGrid(firstImageData, 9, false);
                    
                    // Create a list of icons to avoid for the second grid
                    List<String> iconsToAvoid = createAvoidanceList(firstNineDescriptions, request.getGeneralDescription());
                    
                    // Use image-to-image for second grid if service supports it
                    String secondPrompt = promptGenerationService.generatePromptFor3x3Grid(
                            request.getGeneralDescription(), secondNineDescriptions, iconsToAvoid);
                    
                    if (supportsImageToImage(aiService)) {
                        return generateImageToImageWithService(aiService, secondPrompt, firstImageData, seed)
                                .thenApply(secondImageData -> {
                                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9, true );
                                    
                                    List<String> allIcons = new ArrayList<>(firstGrid);
                                    allIcons.addAll(secondGrid);
                                    
                                    // For 18 icons, use the first grid image as the original reference
                                    return createIconListWithOriginalImage(allIcons, firstImageData, request, serviceName);
                                });
                    } else {
                        // Fallback to regular generation for services that don't support image-to-image
                        return generateImageWithSeed(aiService, secondPrompt, seed)
                                .thenApply(secondImageData -> {
                                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9, true );
                                    
                                    List<String> allIcons = new ArrayList<>(firstGrid);
                                    allIcons.addAll(secondGrid);
                                    
                                    // For 18 icons, use the first grid image as the original reference
                                    return createIconListWithOriginalImage(allIcons, firstImageData, request, serviceName);
                                });
                    }
                });
    }
    
    private CompletableFuture<IconGenerationResult> generateDoubleGridWithReferenceImage(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        // For 18 icons with reference image, generate first grid using image-to-image, then second grid
        List<String> firstNineDescriptions = request.getIndividualDescriptions() != null ? 
                request.getIndividualDescriptions().subList(0, Math.min(9, request.getIndividualDescriptions().size())) : 
                new ArrayList<>();
        
        List<String> secondNineDescriptions = request.getIndividualDescriptions() != null && 
                request.getIndividualDescriptions().size() > 9 ? 
                request.getIndividualDescriptions().subList(9, Math.min(18, request.getIndividualDescriptions().size())) : 
                new ArrayList<>();
        
        String firstPrompt = promptGenerationService.generatePromptForReferenceImage(firstNineDescriptions, request.getGeneralDescription());
        byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());
        
        // Generate first grid using reference image
        return generateImageToImageWithService(aiService, firstPrompt, referenceImageData, seed)
                .thenCompose(firstImageData -> {
                    List<String> firstGrid = imageProcessingService.cropIconsFromGrid(firstImageData, 9, false);
                    
                    // Create a list of icons to avoid for the second grid (consistent with text-based approach)
                    List<String> iconsToAvoid = createAvoidanceList(firstNineDescriptions, null);
                    
                    // For second grid, ALWAYS use the first generated grid as reference for consistency
                    String secondPrompt = promptGenerationService.generatePromptForReferenceImage(secondNineDescriptions, request.getGeneralDescription(), iconsToAvoid);
                    
                    // Always use the first grid as reference for the second grid (consistent with text-based approach)
                    return generateImageToImageWithService(aiService, secondPrompt, firstImageData, seed + 1)
                            .thenApply(secondImageData -> {
                                List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9, true );
                                
                                List<String> allIcons = new ArrayList<>(firstGrid);
                                allIcons.addAll(secondGrid);
                                
                                // For 18 icons, use the first grid image as the original reference
                                return createIconListWithOriginalImage(allIcons, firstImageData, request, serviceName);
                            });
                });
    }
    
    /**
     * Create a list of icon descriptions to avoid when generating the second grid
     * This includes specified descriptions from the first grid
     */
    private List<String> createAvoidanceList(List<String> firstGridDescriptions, String generalTheme) {
        List<String> avoidanceList = new ArrayList<>();
        
        // Add any specific descriptions that were provided for the first grid
        if (firstGridDescriptions != null) {
            firstGridDescriptions.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .forEach(avoidanceList::add);
        }
        
        return avoidanceList;
    }
    
    private List<IconGenerationResponse.GeneratedIcon> createIconList(List<String> base64Icons, IconGenerationRequest request, String serviceName) {
        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();
        
        for (int i = 0; i < base64Icons.size(); i++) {
            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
            icon.setId(UUID.randomUUID().toString());
            icon.setBase64Data(base64Icons.get(i));
            icon.setDescription("");
            icon.setGridPosition(i);
            icon.setServiceSource(serviceName);
            icons.add(icon);
        }
        
        return icons;
    }
    
    /**
     * Helper class to hold both icons and original image data
     */
    private static class IconGenerationResult {
        private final List<IconGenerationResponse.GeneratedIcon> icons;
        private final String originalGridImageBase64;
        
        public IconGenerationResult(List<IconGenerationResponse.GeneratedIcon> icons, String originalGridImageBase64) {
            this.icons = icons;
            this.originalGridImageBase64 = originalGridImageBase64;
        }
        
        public List<IconGenerationResponse.GeneratedIcon> getIcons() {
            return icons;
        }
        
        public String getOriginalGridImageBase64() {
            return originalGridImageBase64;
        }
    }
    
    private IconGenerationResult createIconListWithOriginalImage(List<String> base64Icons, byte[] originalImageData, IconGenerationRequest request, String serviceName) {
        List<IconGenerationResponse.GeneratedIcon> icons = createIconList(base64Icons, request, serviceName);
        String originalGridImageBase64 = Base64.getEncoder().encodeToString(originalImageData);
        return new IconGenerationResult(icons, originalGridImageBase64);
    }
    
    private IconGenerationResponse createTwoModelResponse(String requestId, 
            List<IconGenerationResponse.ServiceResults> gptResults,
            List<IconGenerationResponse.ServiceResults> bananaResults, Long seed) {
        
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setGptResults(gptResults);
        response.setBananaResults(bananaResults);
        response.setSeed(seed);
        
        // Set empty results for other services for backward compatibility
        response.setFalAiResults(new ArrayList<>());
        response.setRecraftResults(new ArrayList<>());
        response.setPhotonResults(new ArrayList<>());
        response.setImagenResults(new ArrayList<>());
        
        // Combine all icons from both models
        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();
        
        // Add icons from GPT and Banana
        addIconsFromServiceResults(allIcons, gptResults);
        addIconsFromServiceResults(allIcons, bananaResults);
        
        response.setIcons(allIcons);
        
        // Set overall status
        int successCount = 0;
        int enabledCount = 0;
        List<String> successfulServices = new ArrayList<>();
        List<String> enabledServices = new ArrayList<>();
        
        // Count successful generations for each model
        successCount += countSuccessfulGenerations(gptResults, "GPT", successfulServices, enabledServices);
        successCount += countSuccessfulGenerations(bananaResults, "Banana", successfulServices, enabledServices);
        
        // Count enabled services (those that have at least one non-disabled result)
        enabledCount = enabledServices.size();
        int totalGenerationsCount = gptResults.size() + bananaResults.size();
        
        if (enabledCount == 0) {
            response.setStatus("error");
            response.setMessage("Both GPT and Banana services are disabled in configuration");
        } else if (successCount > 0) {
            response.setStatus("success");
            if (successCount == totalGenerationsCount) {
                response.setMessage("All generations completed successfully across both models");
            } else {
                response.setMessage(String.format("Generated %d successful generation(s) across models: %s", 
                    successCount, String.join(", ", successfulServices)));
            }
        } else {
            response.setStatus("error");
            response.setMessage("Both enabled models failed to generate icons");
        }
        
        return response;
    }
    
    private void addIconsFromServiceResults(List<IconGenerationResponse.GeneratedIcon> allIcons, List<IconGenerationResponse.ServiceResults> serviceResults) {
        for (IconGenerationResponse.ServiceResults result : serviceResults) {
            if (result.getIcons() != null) {
                allIcons.addAll(result.getIcons());
            }
        }
    }
    
    private int countSuccessfulGenerations(List<IconGenerationResponse.ServiceResults> serviceResults, String serviceName, 
                                          List<String> successfulServices, List<String> enabledServices) {
        int successCount = 0;
        boolean hasEnabledGeneration = false;
        
        for (IconGenerationResponse.ServiceResults result : serviceResults) {
            if (!"disabled".equals(result.getStatus())) {
                hasEnabledGeneration = true;
                if ("success".equals(result.getStatus())) {
                    successCount++;
                }
            }
        }
        
        if (hasEnabledGeneration && !enabledServices.contains(serviceName)) {
            enabledServices.add(serviceName);
        }
        
        if (successCount > 0 && !successfulServices.contains(serviceName)) {
            successfulServices.add(serviceName);
        }
        
        return successCount;
    }
    
    private IconGenerationResponse createErrorResponse(String requestId, String message) {
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setStatus("error");
        response.setMessage(message);
        response.setIcons(new ArrayList<>());
        
        // Create error results for both models with correct generation indices
        IconGenerationResponse.ServiceResults gptErrorResult = new IconGenerationResponse.ServiceResults();
        gptErrorResult.setStatus("error");
        gptErrorResult.setMessage(message);
        gptErrorResult.setIcons(new ArrayList<>());
        gptErrorResult.setGenerationIndex(1); // GPT in pane 1
        
        IconGenerationResponse.ServiceResults bananaErrorResult = new IconGenerationResponse.ServiceResults();
        bananaErrorResult.setStatus("error");
        bananaErrorResult.setMessage(message);
        bananaErrorResult.setIcons(new ArrayList<>());
        bananaErrorResult.setGenerationIndex(2); // Banana in pane 2
        
        response.setFalAiResults(new ArrayList<>());
        response.setRecraftResults(new ArrayList<>());
        response.setPhotonResults(new ArrayList<>());
        response.setGptResults(List.of(gptErrorResult));
        response.setImagenResults(new ArrayList<>());
        response.setBananaResults(List.of(bananaErrorResult));
        
        return response;
    }
    
    private String getDetailedErrorMessage(Throwable error, String serviceName) {
        String message = error.getMessage();
        if (error.getCause() instanceof FalAiException) {
            return error.getCause().getMessage();
        } else if (error instanceof FalAiException) {
            return error.getMessage();
        }
        return serviceName + " service failed: " + (message != null ? message : "Unknown error");
    }
    
    /**
     * Create a modified request with style variation for the second generation
     */
    private IconGenerationRequest createStyledVariationRequest(IconGenerationRequest originalRequest) {
        IconGenerationRequest modifiedRequest = new IconGenerationRequest();
        modifiedRequest.setIconCount(originalRequest.getIconCount());
        modifiedRequest.setIndividualDescriptions(originalRequest.getIndividualDescriptions());
        modifiedRequest.setSeed(originalRequest.getSeed());
        modifiedRequest.setGenerationsPerService(originalRequest.getGenerationsPerService());
        
        // Add style variation to the general description for second generation
        String originalDescription = originalRequest.getGeneralDescription();
        String styledDescription = originalDescription + SECOND_GENERATION_VARIATION;
        modifiedRequest.setGeneralDescription(styledDescription);
        
        return modifiedRequest;
    }
    
    private IconGenerationResponse.ServiceResults createDisabledServiceResult(String serviceName) {
        IconGenerationResponse.ServiceResults result = new IconGenerationResponse.ServiceResults();
        result.setServiceName(serviceName);
        result.setStatus("disabled");
        result.setMessage("Service is disabled in configuration");
        result.setIcons(new ArrayList<>());
        result.setGenerationTimeMs(0L);
        result.setGenerationIndex(1); // Default generation index for disabled services
        return result;
    }
    
    private boolean supportsImageToImage(AIModelService aiService) {
        // Check if the service supports image-to-image generation
        // PhotonModelService and ImagenModelService delegate to other services for image-to-image
        return aiService instanceof FluxModelService || aiService instanceof RecraftModelService || 
               aiService instanceof PhotonModelService || aiService instanceof GptModelService ||
               aiService instanceof ImagenModelService || aiService instanceof BananaModelService;
    }
    
    private CompletableFuture<byte[]> generateImageToImageWithService(AIModelService aiService, String prompt, byte[] sourceImageData, Long seed) {
        log.info("Attempting image-to-image generation with service: {}", aiService.getClass().getSimpleName());
        
        try {
            if (aiService instanceof FluxModelService) {
                log.info("Using FalAiModelService generateImageToImage for image-to-image");
                return ((FluxModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("FalAiModelService image-to-image failed, falling back to regular generation", throwable);
                                return generateImageWithSeed(aiService, prompt, seed).join();
                            }
                            return result;
                        });
            } else if (aiService instanceof RecraftModelService) {
                log.info("Using RecraftModelService generateImageToImage for image-to-image");
                return ((RecraftModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("RecraftModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return generateImageWithSeed(aiService, prompt, seed).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for Recraft", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for Recraft", fallbackError);
                                }
                            }
                            return result;
                        });
            } else if (aiService instanceof PhotonModelService) {
                log.info("Using PhotonModelService generateImageToImage (delegates to Flux) for image-to-image");
                return ((PhotonModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("PhotonModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return generateImageWithSeed(aiService, prompt, seed).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for Photon", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for Photon", fallbackError);
                                }
                            }
                            return result;
                        });
            } else if (aiService instanceof GptModelService) {
                log.info("Using GptModelService generateImageToImage for image-to-image");
                return ((GptModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("GptModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return generateImageWithSeed(aiService, prompt, seed).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for GPT", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for GPT", fallbackError);
                                }
                            }
                            return result;
                        });
            } else if (aiService instanceof ImagenModelService) {
                log.info("Using ImagenModelService generateImageToImage (delegates to GPT) for image-to-image");
                return ((ImagenModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("ImagenModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return generateImageWithSeed(aiService, prompt, seed).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for Imagen", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for Imagen", fallbackError);
                                }
                            }
                            return result;
                        });
            } else if (aiService instanceof BananaModelService) {
                log.info("Using BananaModelService generateImageToImage for image-to-image");
                return ((BananaModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("BananaModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return generateImageWithSeed(aiService, prompt, seed).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for Banana", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for Banana", fallbackError);
                                }
                            }
                            return result;
                        });
            } else {
                log.info("Service doesn't support image-to-image, using regular generation");
                // Fallback to regular generation
                return generateImageWithSeed(aiService, prompt, seed);
            }
        } catch (Exception e) {
            log.error("Error in image-to-image generation, falling back to regular generation", e);
            return generateImageWithSeed(aiService, prompt, seed);
        }
    }
    
    /**
     * Helper method to generate image with optional seed support
     */
    private CompletableFuture<byte[]> generateImageWithSeed(AIModelService aiService, String prompt, Long seed) {
        // Check if the service supports seed parameter by trying specific service types
        if (aiService instanceof FluxModelService) {
            return ((FluxModelService) aiService).generateImage(prompt, seed);
        } else if (aiService instanceof RecraftModelService) {
            return ((RecraftModelService) aiService).generateImage(prompt, seed);
        } else if (aiService instanceof PhotonModelService) {
            return ((PhotonModelService) aiService).generateImage(prompt, seed);
        } else if (aiService instanceof GptModelService) {
            return ((GptModelService) aiService).generateImage(prompt, seed);
        } else if (aiService instanceof ImagenModelService) {
            return ((ImagenModelService) aiService).generateImage(prompt, seed);
        } else if (aiService instanceof BananaModelService) {
            return ((BananaModelService) aiService).generateImage(prompt, seed);
        } else {
            // Fallback to basic generateImage method for unknown service types
            return aiService.generateImage(prompt);
        }
    }
    
    /**
     * Generate a random seed for reproducible results
     */
    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }
    
    /**
     * Persist generated icons to database and file system
     */
    @Transactional
    private void persistGeneratedIcons(String requestId, IconGenerationRequest request, IconGenerationResponse response) {
        try {
            User defaultUser = dataInitializationService.getDefaultUser();
            
            // Get all service results for metadata
            List<IconGenerationResponse.ServiceResults> allServiceResults = new ArrayList<>();
            allServiceResults.addAll(response.getFalAiResults());
            allServiceResults.addAll(response.getRecraftResults());
            allServiceResults.addAll(response.getPhotonResults());
            allServiceResults.addAll(response.getGptResults());
            allServiceResults.addAll(response.getImagenResults());
            allServiceResults.addAll(response.getBananaResults());
            
            // Save individual icons
            for (IconGenerationResponse.GeneratedIcon icon : response.getIcons()) {
                if (icon.getBase64Data() != null && !icon.getBase64Data().isEmpty()) {
                    // Find generation index from service results
                    Integer generationIndex = findGenerationIndex(icon, allServiceResults);
                    
                    // Determine icon type based on generation index
                    String iconType = (generationIndex != null && generationIndex == 1) ? "original" : "variation";
                    
                    // Generate file name without "pos" prefix
                    String fileName = fileStorageService.generateIconFileName(
                            icon.getServiceSource(), 
                            icon.getId(), 
                            icon.getGridPosition()
                    );
                    
                    // Save icon to file system
                    String filePath = fileStorageService.saveIcon(
                            defaultUser.getDirectoryPath(),
                            requestId,
                            iconType,
                            fileName,
                            icon.getBase64Data()
                    );
                    
                    // Create database record
                    GeneratedIcon generatedIcon = new GeneratedIcon();
                    generatedIcon.setRequestId(requestId);
                    generatedIcon.setIconId(icon.getId());
                    generatedIcon.setUser(defaultUser);
                    generatedIcon.setFileName(fileName);
                    generatedIcon.setFilePath(filePath);
                    generatedIcon.setServiceSource(icon.getServiceSource());
                    generatedIcon.setGridPosition(icon.getGridPosition());
                    generatedIcon.setDescription(icon.getDescription());
                    generatedIcon.setTheme(request.getGeneralDescription());
                    generatedIcon.setIconCount(request.getIconCount());
                    generatedIcon.setGenerationIndex(generationIndex);
                    generatedIcon.setIconType(iconType);
                    
                    // Calculate file size
                    long fileSize = fileStorageService.getFileSize(defaultUser.getDirectoryPath(), requestId, iconType, fileName);
                    generatedIcon.setFileSize(fileSize);
                    
                    generatedIconRepository.save(generatedIcon);
                }
            }
            
        } catch (Exception e) {
            log.error("Error persisting icons for request {}", requestId, e);
            throw e;
        }
    }
    
    /**
     * Find the generation index for an icon based on service results
     */
    private Integer findGenerationIndex(IconGenerationResponse.GeneratedIcon icon, List<IconGenerationResponse.ServiceResults> allServiceResults) {
        return allServiceResults.stream()
                .filter(result -> icon.getServiceSource().equals(result.getServiceName()))
                .filter(result -> result.getIcons() != null && result.getIcons().contains(icon))
                .map(IconGenerationResponse.ServiceResults::getGenerationIndex)
                .findFirst()
                .orElse(1);
    }
}
