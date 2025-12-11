package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.ai.AIModelService;
import com.gosu.iconpackgenerator.domain.ai.AnyLlmModelService;
import com.gosu.iconpackgenerator.domain.ai.BananaModelService;
import com.gosu.iconpackgenerator.domain.ai.FluxModelService;
import com.gosu.iconpackgenerator.domain.ai.GptModelService;
import com.gosu.iconpackgenerator.domain.ai.PhotonModelService;
import com.gosu.iconpackgenerator.domain.ai.RecraftModelService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.exception.FalAiException;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.util.ErrorMessageSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.gosu.iconpackgenerator.domain.icons.service.PromptGenerationService.SECOND_GENERATION_VARIATION;

@Service
@RequiredArgsConstructor
@Slf4j
public class IconGenerationService {

    private static final String PROMPT_ENHANCER_SYSTEM_PROMPT = "You are an art director specializing in crafting vivid, cohesive icon pack prompts. Rewrite the user input into a clear, descriptive but concise creative brief. Mention color palette, tone, shapes, and stylistic cues. Keep it under 80 words and in natural sentences without bullet points.";
    private static final String PROMPT_ENHANCER_USER_TEMPLATE = "Original description: \"%s\". Rewrite this so it guides an AI model to design a cohesive icon pack with a unified style, colors, materials, and lighting.";

    private final FluxModelService fluxModelService;
    private final RecraftModelService recraftModelService;
    private final PhotonModelService photonModelService;
    private final GptModelService gptModelService;
    private final BananaModelService bananaModelService;
    private final ImageProcessingService imageProcessingService;
    private final PromptGenerationService promptGenerationService;
    private final AIServicesConfig aiServicesConfig;
    private final CoinManagementService coinManagementService;
    private final ServiceFailureHandler serviceFailureHandler;
    private final IconPersistenceService iconPersistenceService;
    private final TrialModeService trialModeService;
    private final ErrorMessageSanitizer errorMessageSanitizer;
    private final AnyLlmModelService anyLlmModelService;

    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request, User user) {
        return generateIcons(request, UUID.randomUUID().toString(), null, user);
    }

    /**
     * Generate icons with optional progress callback for real-time updates
     */
    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request, String requestId, ProgressUpdateCallback progressCallback, User user) {
        int cost = Math.max(1, request.getGenerationsPerService());

        // Deduct coins using the dedicated service
        CoinManagementService.CoinDeductionResult coinResult = coinManagementService.deductCoinsForGeneration(user, cost);
        if (!coinResult.isSuccess()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, coinResult.getErrorMessage()));
        }

        final boolean isTrialMode = coinResult.isUsedTrialCoins();
        final int deductedCost = coinResult.getDeductedAmount();

        List<String> enabledServices = new ArrayList<>();
        if (aiServicesConfig.isFluxAiEnabled()) enabledServices.add("FalAI");
        if (aiServicesConfig.isRecraftEnabled()) enabledServices.add("Recraft");
        if (aiServicesConfig.isPhotonEnabled()) enabledServices.add("Photon");
        if (aiServicesConfig.isGptEnabled()) enabledServices.add("GPT");
        if (aiServicesConfig.isBananaEnabled()) enabledServices.add("Banana");

        // Generate or use provided seed for consistent results across services
        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();

        applyPromptEnhancementIfRequested(request);

        log.info("Starting icon generation for {} icons with theme: {} using enabled services: {} (seed: {}, trial mode: {}, coin priority: {})",
                request.getIconCount(), request.getGeneralDescription(), enabledServices, seed, isTrialMode, isTrialMode ? "trial coins used as fallback" : "regular coins used");

        // Send initial progress updates only for enabled services
        if (progressCallback != null) {
            if (aiServicesConfig.isFluxAiEnabled()) {
                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "flux", 1), isTrialMode);
                if (request.getGenerationsPerService() > 1) {
                    notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "flux", 2), isTrialMode);
                }
            }

            if (aiServicesConfig.isRecraftEnabled()) {
                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "recraft", 1), isTrialMode);
                if (request.getGenerationsPerService() > 1) {
                    notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "recraft", 2), isTrialMode);
                }
            }

            if (aiServicesConfig.isPhotonEnabled()) {
                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "photon", 1), isTrialMode);
                if (request.getGenerationsPerService() > 1) {
                    notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "photon", 2), isTrialMode);
                }
            }

            if (aiServicesConfig.isGptEnabled()) {
                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "gpt", 1), isTrialMode);
                if (request.getGenerationsPerService() > 1) {
                    notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "gpt", 2), isTrialMode);
                }
            }

            if (aiServicesConfig.isBananaEnabled()) {
                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "banana", 1), isTrialMode);
                if (request.getGenerationsPerService() > 1) {
                    notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, "banana", 2), isTrialMode);
                }
            }
        }

        // Generate multiple generations for each enabled service
        CompletableFuture<List<IconGenerationResponse.ServiceResults>> falAiFuture =
                aiServicesConfig.isFluxAiEnabled() ?
                        generateMultipleGenerationsWithService(request, requestId, fluxModelService, "flux", seed, progressCallback, isTrialMode) :
                        CompletableFuture.completedFuture(List.of(createDisabledServiceResult("flux")));

        CompletableFuture<List<IconGenerationResponse.ServiceResults>> recraftFuture =
                aiServicesConfig.isRecraftEnabled() ?
                        generateMultipleGenerationsWithService(request, requestId, recraftModelService, "recraft", seed, progressCallback, isTrialMode) :
                        CompletableFuture.completedFuture(List.of(createDisabledServiceResult("recraft")));

        CompletableFuture<List<IconGenerationResponse.ServiceResults>> photonFuture =
                aiServicesConfig.isPhotonEnabled() ?
                        generateMultipleGenerationsWithService(request, requestId, photonModelService, "photon", seed, progressCallback, isTrialMode) :
                        CompletableFuture.completedFuture(List.of(createDisabledServiceResult("photon")));

        CompletableFuture<List<IconGenerationResponse.ServiceResults>> gptFuture =
                aiServicesConfig.isGptEnabled() ?
                        generateMultipleGenerationsWithService(request, requestId, gptModelService, "gpt", seed, progressCallback, isTrialMode) :
                        CompletableFuture.completedFuture(List.of(createDisabledServiceResult("gpt")));

        CompletableFuture<List<IconGenerationResponse.ServiceResults>> bananaFuture =
                aiServicesConfig.isBananaEnabled() ?
                        generateMultipleGenerationsWithService(request, requestId, bananaModelService, "banana", seed, progressCallback, isTrialMode) :
                        CompletableFuture.completedFuture(List.of(createDisabledServiceResult("banana")));

        return CompletableFuture.allOf(falAiFuture, recraftFuture, photonFuture, gptFuture, bananaFuture)
                .thenApply(v -> {
                    List<IconGenerationResponse.ServiceResults> falAiResults = falAiFuture.join();
                    List<IconGenerationResponse.ServiceResults> recraftResults = recraftFuture.join();
                    List<IconGenerationResponse.ServiceResults> photonResults = photonFuture.join();
                    List<IconGenerationResponse.ServiceResults> gptResults = gptFuture.join();
                    List<IconGenerationResponse.ServiceResults> bananaResults = bananaFuture.join();

                    IconGenerationResponse finalResponse = createCombinedResponse(requestId, falAiResults, recraftResults, photonResults, gptResults, bananaResults, seed);
                    finalResponse.setTrialMode(isTrialMode);
                    TrialModeService.TrialLimitationResult trialLimitationResult = null;

                    // Check if all enabled services failed due to temporary unavailability and refund coins if needed
                    if ("error".equals(finalResponse.getStatus())) {
                        ServiceFailureHandler.FailureAnalysisResult failureAnalysis =
                                serviceFailureHandler.analyzeServiceFailures(falAiResults, recraftResults, photonResults, gptResults, bananaResults);

                        if (failureAnalysis.shouldRefund()) {
                            try {
                                serviceFailureHandler.processRefund(user, deductedCost, isTrialMode, requestId);
                                finalResponse.setMessage(failureAnalysis.getRefundMessage());
                            } catch (Exception e) {
                                log.error("Failed to refund coins to user {} for request {}", user.getEmail(), requestId, e);
                                // Don't change the error message if refund fails
                            }
                        }
                    }

                    // Apply trial mode limitations if using trial coins
                    if (isTrialMode && "success".equals(finalResponse.getStatus())) {
                        log.info("Applying trial mode limitations to response for request {}", requestId);
                        trialLimitationResult = trialModeService.applyTrialLimitations(finalResponse);
                    }

                    // Persist generated icons to database and file system
                    if ("success".equals(finalResponse.getStatus())) {
                        try {
                            iconPersistenceService.persistGeneratedIcons(requestId, request, finalResponse, user, trialLimitationResult);
                            log.info("Successfully persisted {} icons for request {} (trial mode: {})",
                                    finalResponse.getIcons().size(), requestId, isTrialMode);
                        } catch (Exception e) {
                            log.error("Error persisting icons for request {}", requestId, e);
                            // Don't fail the entire request if persistence fails
                        }
                    }

                    // Send final completion update with limited icons
                    notifyProgressUpdate(progressCallback,
                            ServiceProgressUpdate.allCompleteWithIcons(
                                    requestId, finalResponse.getMessage(), finalResponse.getIcons()),
                            isTrialMode);

                    return finalResponse;
                })
                .exceptionally(error -> {
                    log.error("Error generating icons for request {}", requestId, error);
                    IconGenerationResponse errorResponse = createErrorResponse(requestId, "Failed to generate icons: " + error.getMessage());
                    errorResponse.setTrialMode(isTrialMode);
                    return errorResponse;
                });
    }

    /**
     * Generate multiple independent generations for a single service
     */
    private CompletableFuture<List<IconGenerationResponse.ServiceResults>> generateMultipleGenerationsWithService(
            IconGenerationRequest request, String requestId, AIModelService aiService, String serviceName, Long baseSeed,
            ProgressUpdateCallback progressCallback, boolean isTrialMode) {

        int generationsCount = request.getGenerationsPerService();
        log.info("Generating {} independent generations for service: {}", generationsCount, serviceName);

        List<CompletableFuture<IconGenerationResponse.ServiceResults>> generationFutures = new ArrayList<>();

        for (int i = 0; i < generationsCount; i++) {
            // Use different seed for each generation to ensure variety
            Long generationSeed = baseSeed + i;
            final int generationIndex = i + 1;

            // Create modified request for second generation with style variation
            IconGenerationRequest modifiedRequest = request;
            if (generationIndex == 2) {
                modifiedRequest = createStyledVariationRequest(request);
            }

            CompletableFuture<IconGenerationResponse.ServiceResults> generationFuture = generateIconsWithService(modifiedRequest, requestId, aiService, serviceName, generationSeed)
                    .thenApply(result -> {
                        result.setGenerationIndex(generationIndex);
                        return result;
                    })
                    .whenComplete((result, error) -> {
                        if (progressCallback != null) {
                            String serviceGenName = serviceName + "-gen" + generationIndex;
                            if (error != null) {
                                notifyProgressUpdate(progressCallback,
                                        ServiceProgressUpdate.serviceFailed(
                                                requestId, serviceGenName, getDetailedErrorMessage(error, serviceName),
                                                result != null ? result.getGenerationTimeMs() : 0L, generationIndex),
                                        isTrialMode);
                            } else if ("success".equals(result.getStatus())) {
                                notifyProgressUpdate(progressCallback,
                                        ServiceProgressUpdate.serviceCompleted(
                                                requestId, serviceGenName, result.getIcons(), result.getOriginalGridImageBase64(),
                                                result.getGenerationTimeMs(), generationIndex),
                                        isTrialMode);
                            } else if ("error".equals(result.getStatus())) {
                                notifyProgressUpdate(progressCallback,
                                        ServiceProgressUpdate.serviceFailed(
                                                requestId, serviceGenName, result.getMessage(), result.getGenerationTimeMs(),
                                                generationIndex),
                                        isTrialMode);
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
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false);
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
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false);
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
                                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9, true);

                                    List<String> allIcons = new ArrayList<>(firstGrid);
                                    allIcons.addAll(secondGrid);

                                    // For 18 icons, use the first grid image as the original reference
                                    return createIconListWithOriginalImage(allIcons, firstImageData, request, serviceName);
                                });
                    } else {
                        // Fallback to regular generation for services that don't support image-to-image
                        return generateImageWithSeed(aiService, secondPrompt, seed)
                                .thenApply(secondImageData -> {
                                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9, true);

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
                                List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9, true);

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

    private IconGenerationResponse createCombinedResponse(String requestId,
                                                          List<IconGenerationResponse.ServiceResults> falAiResults,
                                                          List<IconGenerationResponse.ServiceResults> recraftResults,
                                                          List<IconGenerationResponse.ServiceResults> photonResults,
                                                          List<IconGenerationResponse.ServiceResults> gptResults,
                                                          List<IconGenerationResponse.ServiceResults> bananaResults, Long seed) {

        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setFalAiResults(falAiResults);
        response.setRecraftResults(recraftResults);
        response.setPhotonResults(photonResults);
        response.setGptResults(gptResults);
        response.setBananaResults(bananaResults);
        response.setSeed(seed);

        // Combine all icons from all generations for backward compatibility
        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();

        // Add icons from all generations of each service
        addIconsFromServiceResults(allIcons, falAiResults);
        addIconsFromServiceResults(allIcons, recraftResults);
        addIconsFromServiceResults(allIcons, photonResults);
        addIconsFromServiceResults(allIcons, gptResults);
        addIconsFromServiceResults(allIcons, bananaResults);

        response.setIcons(allIcons);

        // Set overall status
        int successCount = 0;
        int enabledCount = 0;
        int totalGenerationsCount = 0;
        List<String> successfulServices = new ArrayList<>();
        List<String> enabledServices = new ArrayList<>();

        // Count successful generations for each service
        successCount += countSuccessfulGenerations(falAiResults, "Flux-Pro", successfulServices, enabledServices);
        successCount += countSuccessfulGenerations(recraftResults, "Recraft", successfulServices, enabledServices);
        successCount += countSuccessfulGenerations(photonResults, "Photon", successfulServices, enabledServices);
        successCount += countSuccessfulGenerations(gptResults, "GPT", successfulServices, enabledServices);
        successCount += countSuccessfulGenerations(bananaResults, "Banana", successfulServices, enabledServices);

        // Count enabled services (those that have at least one non-disabled result)
        enabledCount = enabledServices.size();
        totalGenerationsCount = falAiResults.size() + recraftResults.size() + photonResults.size() + gptResults.size() + bananaResults.size();

        if (enabledCount == 0) {
            response.setStatus("error");
            response.setMessage("All AI services are disabled in configuration");
        } else if (successCount > 0) {
            response.setStatus("success");
            if (successCount == totalGenerationsCount) {
                response.setMessage("All generations completed successfully across all enabled services");
            } else {
                response.setMessage(String.format("Generated %d successful generation(s) across services: %s",
                        successCount, String.join(", ", successfulServices)));
            }
        } else {
            response.setStatus("error");
            response.setMessage("All enabled services failed to generate icons");
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

        IconGenerationResponse.ServiceResults errorResult = new IconGenerationResponse.ServiceResults();
        errorResult.setStatus("error");
        errorResult.setMessage(message);
        errorResult.setIcons(new ArrayList<>());
        errorResult.setGenerationIndex(1);

        List<IconGenerationResponse.ServiceResults> errorList = List.of(errorResult);

        response.setFalAiResults(errorList);
        response.setRecraftResults(errorList);
        response.setPhotonResults(errorList);
        response.setGptResults(errorList);
        response.setBananaResults(errorList);

        return response;
    }

    private String getDetailedErrorMessage(Throwable error, String serviceName) {
        String originalMessage = error.getMessage();

        if (error.getCause() instanceof FalAiException) {
            originalMessage = error.getCause().getMessage();
        } else if (error instanceof FalAiException) {
            originalMessage = error.getMessage();
        } else if (originalMessage == null) {
            originalMessage = serviceName + " service failed: Unknown error";
        }

        return errorMessageSanitizer.sanitizeErrorMessage(originalMessage, serviceName);
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
        if (originalRequest.hasReferenceImage()) {
            modifiedRequest.setReferenceImageBase64(originalRequest.getReferenceImageBase64());
        }
        modifiedRequest.setEnhancePrompt(false);

        return modifiedRequest;
    }

    private void applyPromptEnhancementIfRequested(IconGenerationRequest request) {
        if (!request.isEnhancePrompt() || request.hasReferenceImage()) {
            return;
        }

        String originalDescription = request.getGeneralDescription();
        if (originalDescription == null) {
            return;
        }

        String trimmed = originalDescription.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        try {
            log.info("Enhancing icon prompt with AnyLlmModelService");
            String detailedPrompt = anyLlmModelService
                    .generateCompletion(String.format(PROMPT_ENHANCER_USER_TEMPLATE, trimmed), PROMPT_ENHANCER_SYSTEM_PROMPT)
                    .join();
            if (detailedPrompt != null) {
                String cleaned = detailedPrompt.trim();
                if (!cleaned.isEmpty()) {
                    log.debug("Prompt enhanced from '{}' to '{}'", trimmed, cleaned);
                    request.setGeneralDescription(cleaned);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enhance icon prompt, falling back to original description", e);
        }
    }

    private IconGenerationResponse.ServiceResults createDisabledServiceResult(String serviceName) {
        IconGenerationResponse.ServiceResults result = new IconGenerationResponse.ServiceResults();
        result.setServiceName(serviceName);
        result.setStatus("disabled");
        result.setMessage("Service is disabled in configuration");
        result.setIcons(new ArrayList<>());
        result.setGenerationTimeMs(0L);
        result.setGenerationIndex(1);
        return result;
    }

    private boolean supportsImageToImage(AIModelService aiService) {
        // Check if the service supports image-to-image generation
        // PhotonModelService and BananaModelService support or delegate image-to-image
        return aiService instanceof FluxModelService || aiService instanceof RecraftModelService ||
                aiService instanceof PhotonModelService || aiService instanceof GptModelService ||
                aiService instanceof BananaModelService;
    }

    private CompletableFuture<byte[]> generateImageToImageWithService(AIModelService aiService, String prompt, byte[] sourceImageData, Long seed) {
        log.info("Attempting image-to-image generation with service: {}", aiService.getClass().getSimpleName());

        try {
            if (aiService instanceof FluxModelService) {
                log.info("Using FalAiModelService generateImageToImage for image-to-image");
                return ((FluxModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("FalAiModelService image-to-image failed", throwable);
                            }
                            return result;
                        });
            } else if (aiService instanceof RecraftModelService) {
                log.info("Using RecraftModelService generateImageToImage for image-to-image");
                return ((RecraftModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("RecraftModelService image-to-image failed", throwable);
                            }
                            return result;
                        });
            } else if (aiService instanceof PhotonModelService) {
                log.info("Using PhotonModelService generateImageToImage (delegates to Flux) for image-to-image");
                return ((PhotonModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("PhotonModelService image-to-image failed", throwable);
                            }
                            return result;
                        });
            } else if (aiService instanceof GptModelService) {
                log.info("Using GptModelService generateImageToImage for image-to-image");
                return ((GptModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("GptModelService image-to-image failedc", throwable);
                            }
                            return result;
                        });
            } else if (aiService instanceof BananaModelService) {
                log.info("Using BananaModelService generateImageToImage for image-to-image");
                return ((BananaModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("BananaModelService image-to-image failed", throwable);
                            }
                            return result;
                        });
            } else {
                log.info("Service doesn't support image-to-image, using regular generation");
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
}
