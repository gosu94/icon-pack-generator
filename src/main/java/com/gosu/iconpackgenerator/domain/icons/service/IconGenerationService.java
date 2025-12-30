package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.ai.AnyLlmModelService;
import com.gosu.iconpackgenerator.domain.ai.Gpt15ModelService;
import com.gosu.iconpackgenerator.domain.ai.GptModelService;
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

    private final GptModelService gptModelService;
    private final Gpt15ModelService gpt15ModelService;
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
    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request,
                                                                   String requestId,
                                                                   ProgressUpdateCallback progressCallback,
                                                                   User user) {
        if (!aiServicesConfig.isGptEnabled()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, "Icon generation service is disabled."));
        }

        if (request.getGenerationsPerService() > 1 && !aiServicesConfig.isGpt15Enabled()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, "Variations are unavailable because GPT 1.5 service is disabled."));
        }

        int cost = Math.max(1, request.getGenerationsPerService());
        CoinManagementService.CoinDeductionResult coinResult = coinManagementService.deductCoinsForGeneration(user, cost);
        if (!coinResult.isSuccess()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, coinResult.getErrorMessage()));
        }

        final boolean isTrialMode = coinResult.isUsedTrialCoins();
        final int deductedCost = coinResult.getDeductedAmount();

        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();
        applyPromptEnhancementIfRequested(request);

        log.info("Starting icon generation for {} icons with GPT service (requestId={}, seed={}, trialMode={})",
                request.getIconCount(), requestId, seed, isTrialMode);

        if (progressCallback != null) {
            for (int genIndex = 1; genIndex <= request.getGenerationsPerService(); genIndex++) {
                String serviceName = shouldUseGpt15(genIndex, request) ? "gpt15" : "gpt";
                String serviceKey = serviceName + "-gen" + genIndex;
                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, serviceKey, genIndex), isTrialMode);
            }
        }

        return generateGptGenerations(request, requestId, seed, progressCallback, isTrialMode)
                .thenApply(combinedResults -> {
                    IconGenerationResponse finalResponse = createCombinedResponse(requestId, combinedResults.gptResults(), combinedResults.gpt15Results(), seed);
                    finalResponse.setTrialMode(isTrialMode);

                    if ("error".equals(finalResponse.getStatus())) {
                        ServiceFailureHandler.FailureAnalysisResult failureAnalysis =
                                serviceFailureHandler.analyzeServiceFailures(
                                        List.of(), List.of(), List.of(),
                                        combinedResults.gptResults(),
                                        combinedResults.gpt15Results(),
                                        List.of());

                        if (failureAnalysis.shouldRefund()) {
                            try {
                                serviceFailureHandler.processRefund(user, deductedCost, isTrialMode, requestId);
                                finalResponse.setMessage(failureAnalysis.getRefundMessage());
                            } catch (Exception e) {
                                log.error("Failed to refund coins to user {} for request {}", user.getEmail(), requestId, e);
                            }
                        } else {
                            finalResponse.setMessage(failureAnalysis.getOriginalMessage());
                        }
                    }

                    if ("success".equals(finalResponse.getStatus())) {
                        try {
                            if (isTrialMode) {
                                iconPersistenceService.persistGeneratedIcons(requestId, request, finalResponse, user, false, true);
                                trialModeService.applyTrialWatermark(finalResponse);
                                iconPersistenceService.persistGeneratedIcons(requestId, request, finalResponse, user, true, false);
                            } else {
                                iconPersistenceService.persistGeneratedIcons(requestId, request, finalResponse, user, false, false);
                            }
                            log.info("Persisted {} icons for request {} (trialMode={})",
                                    finalResponse.getIcons().size(), requestId, isTrialMode);
                        } catch (Exception e) {
                            log.error("Error persisting icons for request {}", requestId, e);
                        }
                    }

                    notifyProgressUpdate(progressCallback,
                            ServiceProgressUpdate.allCompleteWithIcons(requestId, finalResponse.getMessage(), finalResponse.getIcons()),
                            isTrialMode);

                    return finalResponse;
                })
                .exceptionally(error -> {
                    log.error("Error generating icons for request {}", requestId, error);
                    IconGenerationResponse response = createErrorResponse(requestId,
                            "Failed to generate icons: " + error.getMessage());
                    response.setTrialMode(isTrialMode);
                    return response;
                });
    }

    private CompletableFuture<CombinedServiceResults> generateGptGenerations(
            IconGenerationRequest request,
            String requestId,
            Long baseSeed,
            ProgressUpdateCallback progressCallback,
            boolean isTrialMode) {

        int generations = Math.max(1, request.getGenerationsPerService());
        List<CompletableFuture<ServiceGenerationResult>> futures = new ArrayList<>();

        for (int i = 0; i < generations; i++) {
            final int generationIndex = i + 1;
            final long generationSeed = baseSeed + i;
            IconGenerationRequest generationRequest = generationIndex == 2
                    ? createStyledVariationRequest(request)
                    : request;

            boolean useGpt15 = shouldUseGpt15(generationIndex, request);
            String serviceName = useGpt15 ? "gpt15" : "gpt";

            futures.add(generateSingleGeneration(
                    generationRequest,
                    requestId,
                    generationSeed,
                    generationIndex,
                    serviceName,
                    useGpt15,
                    progressCallback,
                    isTrialMode));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<IconGenerationResponse.ServiceResults> gptResults = new ArrayList<>();
                    List<IconGenerationResponse.ServiceResults> gpt15Results = new ArrayList<>();

                    futures.stream()
                            .map(CompletableFuture::join)
                            .forEach(result -> {
                                if ("gpt15".equals(result.serviceName())) {
                                    gpt15Results.add(result.serviceResults());
                                } else {
                                    gptResults.add(result.serviceResults());
                                }
                            });

                    return new CombinedServiceResults(gptResults, gpt15Results);
                });
    }

    private CompletableFuture<ServiceGenerationResult> generateSingleGeneration(
            IconGenerationRequest request,
            String requestId,
            Long seed,
            int generationIndex,
            String serviceName,
            boolean useGpt15,
            ProgressUpdateCallback progressCallback,
            boolean isTrialMode) {

        long startTime = System.currentTimeMillis();
        return generateIconsInternal(request, seed, useGpt15)
                .thenApply(result -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    IconGenerationResponse.ServiceResults serviceResult = new IconGenerationResponse.ServiceResults();
                    serviceResult.setServiceName(serviceName);
                    serviceResult.setStatus("success");
                    serviceResult.setMessage("Icons generated successfully");
                    serviceResult.setIcons(result.getIcons());
                    serviceResult.setOriginalGridImageBase64(result.getOriginalGridImageBase64());
                    serviceResult.setGenerationTimeMs(generationTime);
                    serviceResult.setGenerationIndex(generationIndex);

                    notifyProgressUpdate(progressCallback,
                            ServiceProgressUpdate.serviceCompleted(
                                    requestId,
                                    serviceName + "-gen" + generationIndex,
                                    result.getIcons(),
                                    result.getOriginalGridImageBase64(),
                                    generationTime,
                                    generationIndex),
                            isTrialMode);

                    return new ServiceGenerationResult(serviceName, serviceResult);
                })
                .exceptionally(error -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    String detailedServiceName = useGpt15 ? "GPT 1.5" : "GPT";
                    String message = getDetailedErrorMessage(error, detailedServiceName);

                    IconGenerationResponse.ServiceResults serviceResult = new IconGenerationResponse.ServiceResults();
                    serviceResult.setServiceName(serviceName);
                    serviceResult.setStatus("error");
                    serviceResult.setMessage(message);
                    serviceResult.setIcons(new ArrayList<>());
                    serviceResult.setGenerationTimeMs(generationTime);
                    serviceResult.setGenerationIndex(generationIndex);

                    notifyProgressUpdate(progressCallback,
                            ServiceProgressUpdate.serviceFailed(
                                    requestId,
                                    serviceName + "-gen" + generationIndex,
                                    message,
                                    generationTime,
                                    generationIndex),
                            isTrialMode);

                    return new ServiceGenerationResult(serviceName, serviceResult);
                });
    }

    private CompletableFuture<IconGenerationResult> generateIconsInternal(IconGenerationRequest request, Long seed, boolean useGpt15) {
        if (request.hasReferenceImage()) {
            return generateGridWithReferenceImage(request, seed, useGpt15);
        }
        return generateGridWithTextPrompt(request, seed, useGpt15);
    }

    private CompletableFuture<IconGenerationResult> generateGridWithTextPrompt(IconGenerationRequest request, Long seed, boolean useGpt15) {
        String prompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(),
                request.getIndividualDescriptions());

        String serviceName = useGpt15 ? "gpt15" : "gpt";
        CompletableFuture<byte[]> imageFuture = useGpt15
                ? gpt15ModelService.generateImage(prompt, seed)
                : gptModelService.generateImage(prompt, seed);

        return imageFuture
                .thenApply(imageData -> {
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false);
                    return createIconListWithOriginalImage(base64Icons, imageData, serviceName);
                });
    }

    private CompletableFuture<IconGenerationResult> generateGridWithReferenceImage(IconGenerationRequest request, Long seed, boolean useGpt15) {
        String prompt = promptGenerationService.generatePromptForReferenceImage(
                request.getIndividualDescriptions(),
                request.getGeneralDescription());

        byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());
        String serviceName = useGpt15 ? "gpt15" : "gpt";

        CompletableFuture<byte[]> imageFuture = useGpt15
                ? gpt15ModelService.generateImageToImage(prompt, referenceImageData, seed)
                : gptModelService.generateImageToImage(prompt, referenceImageData, seed);

        return imageFuture
                .thenApply(imageData -> {
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false);
                    return createIconListWithOriginalImage(base64Icons, imageData, serviceName);
                });
    }

    private IconGenerationResponse createCombinedResponse(String requestId,
                                                          List<IconGenerationResponse.ServiceResults> gptResults,
                                                          List<IconGenerationResponse.ServiceResults> gpt15Results,
                                                          Long seed) {
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setSeed(seed);
        response.setFalAiResults(new ArrayList<>());
        response.setRecraftResults(new ArrayList<>());
        response.setPhotonResults(new ArrayList<>());
        response.setGptResults(new ArrayList<>(gptResults));
        response.setGpt15Results(new ArrayList<>(gpt15Results));
        response.setBananaResults(new ArrayList<>());

        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();
        addIconsFromServiceResults(allIcons, gptResults);
        addIconsFromServiceResults(allIcons, gpt15Results);
        response.setIcons(allIcons);

        boolean anySuccess = gptResults.stream().anyMatch(result -> "success".equals(result.getStatus())) ||
                gpt15Results.stream().anyMatch(result -> "success".equals(result.getStatus()));
        if (anySuccess) {
            response.setStatus("success");
            response.setMessage("Icon generation completed successfully");
        } else {
            response.setStatus("error");
            response.setMessage("Icon generation failed");
        }

        return response;
    }

    private void addIconsFromServiceResults(List<IconGenerationResponse.GeneratedIcon> allIcons,
                                            List<IconGenerationResponse.ServiceResults> serviceResults) {
        if (serviceResults == null) {
            return;
        }
        for (IconGenerationResponse.ServiceResults result : serviceResults) {
            if (result.getIcons() != null) {
                allIcons.addAll(result.getIcons());
            }
        }
    }

    private IconGenerationResponse createErrorResponse(String requestId, String message) {
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setStatus("error");
        response.setMessage(message);
        response.setIcons(new ArrayList<>());
        response.setFalAiResults(new ArrayList<>());
        response.setRecraftResults(new ArrayList<>());
        response.setPhotonResults(new ArrayList<>());
        response.setGpt15Results(new ArrayList<>());
        response.setBananaResults(new ArrayList<>());

        IconGenerationResponse.ServiceResults errorResult = new IconGenerationResponse.ServiceResults();
        errorResult.setServiceName("gpt");
        errorResult.setStatus("error");
        errorResult.setMessage(message);
        errorResult.setIcons(new ArrayList<>());
        errorResult.setGenerationIndex(1);

        List<IconGenerationResponse.ServiceResults> gptResults = new ArrayList<>();
        gptResults.add(errorResult);
        response.setGptResults(gptResults);
        return response;
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

    private void applyPromptEnhancementIfRequested(IconGenerationRequest request) {
        if (!request.isEnhancePrompt()) {
            return;
        }

        if (request.getGeneralDescription() == null) {
            return;
        }

        String trimmed = request.getGeneralDescription().trim();
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

    private IconGenerationRequest createStyledVariationRequest(IconGenerationRequest originalRequest) {
        IconGenerationRequest modifiedRequest = new IconGenerationRequest();
        modifiedRequest.setIconCount(originalRequest.getIconCount());
        modifiedRequest.setIndividualDescriptions(originalRequest.getIndividualDescriptions());
        modifiedRequest.setSeed(originalRequest.getSeed());
        modifiedRequest.setGenerationsPerService(originalRequest.getGenerationsPerService());
        modifiedRequest.setReferenceImageBase64(originalRequest.getReferenceImageBase64());
        modifiedRequest.setEnhancePrompt(originalRequest.isEnhancePrompt());

        String originalDescription = originalRequest.getGeneralDescription() != null
                ? originalRequest.getGeneralDescription()
                : "";
        String styledDescription = originalDescription + SECOND_GENERATION_VARIATION;
        modifiedRequest.setGeneralDescription(styledDescription);
        return modifiedRequest;
    }

    private IconGenerationResult createIconListWithOriginalImage(List<String> base64Icons,
                                                                 byte[] originalImageData,
                                                                 String serviceName) {
        List<IconGenerationResponse.GeneratedIcon> icons = createIconList(base64Icons, serviceName);
        String originalGridImageBase64 = Base64.getEncoder().encodeToString(originalImageData);
        return new IconGenerationResult(icons, originalGridImageBase64);
    }

    private List<IconGenerationResponse.GeneratedIcon> createIconList(List<String> base64Icons, String serviceName) {
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

    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }

    private String getDetailedErrorMessage(Throwable error, String serviceName) {
        Throwable rootCause = error instanceof FalAiException ? error : error.getCause();
        String originalMessage = rootCause != null ? rootCause.getMessage() : error.getMessage();
        if (originalMessage == null || originalMessage.isEmpty()) {
            originalMessage = serviceName + " service failed: Unknown error";
        }
        return errorMessageSanitizer.sanitizeErrorMessage(originalMessage, serviceName);
    }

    private static class CombinedServiceResults {
        private final List<IconGenerationResponse.ServiceResults> gptResults;
        private final List<IconGenerationResponse.ServiceResults> gpt15Results;

        CombinedServiceResults(List<IconGenerationResponse.ServiceResults> gptResults,
                               List<IconGenerationResponse.ServiceResults> gpt15Results) {
            this.gptResults = gptResults;
            this.gpt15Results = gpt15Results;
        }

        public List<IconGenerationResponse.ServiceResults> gptResults() {
            return gptResults;
        }

        public List<IconGenerationResponse.ServiceResults> gpt15Results() {
            return gpt15Results;
        }
    }

    private static class ServiceGenerationResult {
        private final String serviceName;
        private final IconGenerationResponse.ServiceResults serviceResults;

        ServiceGenerationResult(String serviceName, IconGenerationResponse.ServiceResults serviceResults) {
            this.serviceName = serviceName;
            this.serviceResults = serviceResults;
        }

        public String serviceName() {
            return serviceName;
        }

        public IconGenerationResponse.ServiceResults serviceResults() {
            return serviceResults;
        }
    }

    private static class IconGenerationResult {
        private final List<IconGenerationResponse.GeneratedIcon> icons;
        private final String originalGridImageBase64;

        IconGenerationResult(List<IconGenerationResponse.GeneratedIcon> icons, String originalGridImageBase64) {
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

    private boolean shouldUseGpt15(int generationIndex, IconGenerationRequest request) {
        return generationIndex == 2 &&
                request.getGenerationsPerService() >= 2 &&
                aiServicesConfig.isGpt15Enabled();
    }
}
