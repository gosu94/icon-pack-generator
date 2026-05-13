package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.ai.Gpt15ModelService;
import com.gosu.iconpackgenerator.domain.ai.Gpt2ModelService;
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
    private static final String MODEL_STANDARD = "standard";
    private static final String MODEL_PRO = "pro";
    private static final String MODEL_PRO_PLUS = "pro_plus";

    private final GptModelService gptModelService;
    private final Gpt15ModelService gpt15ModelService;
    private final Gpt2ModelService gpt2ModelService;
    private final ImageProcessingService imageProcessingService;
    private final PromptGenerationService promptGenerationService;
    private final AIServicesConfig aiServicesConfig;
    private final CoinManagementService coinManagementService;
    private final ServiceFailureHandler serviceFailureHandler;
    private final IconPersistenceService iconPersistenceService;
    private final TrialModeService trialModeService;
    private final ErrorMessageSanitizer errorMessageSanitizer;
    private final IconPromptEnhancementService iconPromptEnhancementService;
    private final LogoDescriptionGenerationService logoDescriptionGenerationService;

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
        if (usesGptModel(request) && !aiServicesConfig.isGptEnabled()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, "Icon generation service is disabled."));
        }

        if (usesGpt15Model(request) && !aiServicesConfig.isGpt15Enabled()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, "Pro model generation is unavailable because the service is disabled."));
        }

        if (usesGpt2Model(request) && !aiServicesConfig.isGpt2Enabled()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, "Pro+ model generation is unavailable because the service is disabled."));
        }

        int cost = calculateGenerationCost(request);
        CoinManagementService.CoinDeductionResult coinResult = usesGpt2Model(request)
                ? coinManagementService.deductRegularCoins(user, cost)
                : coinManagementService.deductCoinsForGeneration(user, cost);
        if (!coinResult.isSuccess()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, coinResult.getErrorMessage()));
        }

        final boolean isTrialMode = coinResult.isUsedTrialCoins();
        final int deductedCost = coinResult.getDeductedAmount();

        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();
        applyPromptEnhancementIfRequested(request);
        applyLogoDescriptionsIfRequested(request);

        log.info("Starting icon generation for {} icons with GPT service (requestId={}, seed={}, trialMode={})",
                request.getIconCount(), requestId, seed, isTrialMode);

        if (progressCallback != null) {
            for (int genIndex = 1; genIndex <= request.getGenerationsPerService(); genIndex++) {
                String serviceName = getServiceNameForGeneration(genIndex, request);
                String serviceKey = serviceName + "-gen" + genIndex;
                notifyProgressUpdate(progressCallback, ServiceProgressUpdate.serviceStarted(requestId, serviceKey, genIndex), isTrialMode);
            }
        }

        return generateGptGenerations(request, requestId, seed, progressCallback, isTrialMode)
                .thenApply(combinedResults -> {
                    IconGenerationResponse finalResponse = createCombinedResponse(
                            requestId,
                            combinedResults.gptResults(),
                            combinedResults.gpt15Results(),
                            combinedResults.gpt2Results(),
                            seed);
                    finalResponse.setTrialMode(isTrialMode);

                    if ("error".equals(finalResponse.getStatus())) {
                        ServiceFailureHandler.FailureAnalysisResult failureAnalysis =
                                serviceFailureHandler.analyzeServiceFailures(
                                        List.of(), List.of(), List.of(),
                                        combinedResults.gptResults(),
                                        mergeServiceResults(combinedResults.gpt15Results(), combinedResults.gpt2Results()),
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

            String serviceName = getServiceNameForGeneration(generationIndex, request);

            futures.add(generateSingleGeneration(
                    generationRequest,
                    requestId,
                    generationSeed,
                    generationIndex,
                    serviceName,
                    progressCallback,
                    isTrialMode));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<IconGenerationResponse.ServiceResults> gptResults = new ArrayList<>();
                    List<IconGenerationResponse.ServiceResults> gpt15Results = new ArrayList<>();
                    List<IconGenerationResponse.ServiceResults> gpt2Results = new ArrayList<>();

                    futures.stream()
                            .map(CompletableFuture::join)
                            .forEach(result -> {
                                if ("gpt15".equals(result.serviceName())) {
                                    gpt15Results.add(result.serviceResults());
                                } else if ("gpt2".equals(result.serviceName())) {
                                    gpt2Results.add(result.serviceResults());
                                } else {
                                    gptResults.add(result.serviceResults());
                                }
                            });

                    return new CombinedServiceResults(gptResults, gpt15Results, gpt2Results);
                });
    }

    private CompletableFuture<ServiceGenerationResult> generateSingleGeneration(
            IconGenerationRequest request,
            String requestId,
            Long seed,
            int generationIndex,
            String serviceName,
            ProgressUpdateCallback progressCallback,
            boolean isTrialMode) {

        long startTime = System.currentTimeMillis();
        return generateIconsInternal(request, seed, serviceName)
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
                    String detailedServiceName = getDetailedServiceName(serviceName);
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

    private CompletableFuture<IconGenerationResult> generateIconsInternal(IconGenerationRequest request, Long seed, String serviceName) {
        if (request.hasReferenceImage()) {
            return generateGridWithReferenceImage(request, seed);
        }
        return generateGridWithTextPrompt(request, seed, serviceName);
    }

    private CompletableFuture<IconGenerationResult> generateGridWithTextPrompt(IconGenerationRequest request, Long seed, String serviceName) {
        String prompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(),
                request.getIndividualDescriptions());

        CompletableFuture<byte[]> imageFuture = generateTextImage(serviceName, prompt, seed);

        return imageFuture
                .thenApply(imageData -> {
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false);
                    return createIconListWithOriginalImage(base64Icons, imageData, serviceName);
                });
    }

    private CompletableFuture<IconGenerationResult> generateGridWithReferenceImage(IconGenerationRequest request, Long seed) {
        String prompt = promptGenerationService.generatePromptForReferenceImage(
                request.getIndividualDescriptions(),
                request.getGeneralDescription());

        byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());
        String serviceName = "gpt15";

        CompletableFuture<byte[]> imageFuture = gpt15ModelService.generateImageToImage(prompt, referenceImageData, seed);

        return imageFuture
                .thenApply(imageData -> {
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false);
                    return createIconListWithOriginalImage(base64Icons, imageData, serviceName);
                });
    }

    private IconGenerationResponse createCombinedResponse(String requestId,
                                                          List<IconGenerationResponse.ServiceResults> gptResults,
                                                          List<IconGenerationResponse.ServiceResults> gpt15Results,
                                                          List<IconGenerationResponse.ServiceResults> gpt2Results,
                                                          Long seed) {
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setSeed(seed);
        response.setFalAiResults(new ArrayList<>());
        response.setRecraftResults(new ArrayList<>());
        response.setPhotonResults(new ArrayList<>());
        response.setGptResults(new ArrayList<>(gptResults));
        response.setGpt15Results(new ArrayList<>(gpt15Results));
        response.setGpt2Results(new ArrayList<>(gpt2Results));
        response.setBananaResults(new ArrayList<>());

        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();
        addIconsFromServiceResults(allIcons, gptResults);
        addIconsFromServiceResults(allIcons, gpt15Results);
        addIconsFromServiceResults(allIcons, gpt2Results);
        response.setIcons(allIcons);

        boolean anySuccess = gptResults.stream().anyMatch(result -> "success".equals(result.getStatus())) ||
                gpt15Results.stream().anyMatch(result -> "success".equals(result.getStatus())) ||
                gpt2Results.stream().anyMatch(result -> "success".equals(result.getStatus()));
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
        response.setGpt2Results(new ArrayList<>());
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

        request.setGeneralDescription(iconPromptEnhancementService.enhanceIfPossible(trimmed));
    }

    private void applyLogoDescriptionsIfRequested(IconGenerationRequest request) {
        if (!request.isDesignLogo()) {
            return;
        }

        if (request.hasReferenceImage()) {
            log.info("Skipping automatic logo descriptions because the request uses a reference image");
            return;
        }

        String generalDescription = request.getGeneralDescription();
        if (generalDescription == null || generalDescription.trim().isEmpty()) {
            return;
        }

        List<String> generatedDescriptions = logoDescriptionGenerationService.generateDescriptions(generalDescription);
        request.setIndividualDescriptions(new ArrayList<>(generatedDescriptions));
        log.info("Generated {} automatic logo descriptions for theme '{}'",
                generatedDescriptions.size(),
                generalDescription.substring(0, Math.min(80, generalDescription.length())));
    }

    private IconGenerationRequest createStyledVariationRequest(IconGenerationRequest originalRequest) {
        IconGenerationRequest modifiedRequest = new IconGenerationRequest();
        modifiedRequest.setIconCount(originalRequest.getIconCount());
        modifiedRequest.setIndividualDescriptions(originalRequest.getIndividualDescriptions());
        modifiedRequest.setSeed(originalRequest.getSeed());
        modifiedRequest.setGenerationsPerService(originalRequest.getGenerationsPerService());
        modifiedRequest.setReferenceImageBase64(originalRequest.getReferenceImageBase64());
        modifiedRequest.setEnhancePrompt(originalRequest.isEnhancePrompt());
        modifiedRequest.setDesignLogo(originalRequest.isDesignLogo());
        modifiedRequest.setBaseModel(originalRequest.getBaseModel());
        modifiedRequest.setVariationModel(originalRequest.getVariationModel());

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

    private CompletableFuture<byte[]> generateTextImage(String serviceName, String prompt, Long seed) {
        return switch (serviceName) {
            case "gpt15" -> gpt15ModelService.generateImage(prompt, seed);
            case "gpt2" -> gpt2ModelService.generateImage(prompt, seed);
            default -> gptModelService.generateImage(prompt, seed);
        };
    }

    private String getDetailedServiceName(String serviceName) {
        return switch (serviceName) {
            case "gpt15" -> "GPT 1.5";
            case "gpt2" -> "GPT 2";
            default -> "GPT";
        };
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
        private final List<IconGenerationResponse.ServiceResults> gpt2Results;

        CombinedServiceResults(List<IconGenerationResponse.ServiceResults> gptResults,
                               List<IconGenerationResponse.ServiceResults> gpt15Results,
                               List<IconGenerationResponse.ServiceResults> gpt2Results) {
            this.gptResults = gptResults;
            this.gpt15Results = gpt15Results;
            this.gpt2Results = gpt2Results;
        }

        public List<IconGenerationResponse.ServiceResults> gptResults() {
            return gptResults;
        }

        public List<IconGenerationResponse.ServiceResults> gpt15Results() {
            return gpt15Results;
        }

        public List<IconGenerationResponse.ServiceResults> gpt2Results() {
            return gpt2Results;
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

    private String getServiceNameForGeneration(int generationIndex, IconGenerationRequest request) {
        if (request.hasReferenceImage()) {
            return "gpt15";
        }

        String selectedModel = generationIndex == 1
                ? request.getBaseModel()
                : request.getVariationModel();

        if (isProPlusModel(selectedModel)) {
            return "gpt2";
        }

        if (isProModel(selectedModel)) {
            return "gpt15";
        }

        return "gpt";
    }

    private int calculateGenerationCost(IconGenerationRequest request) {
        int generations = Math.max(1, request.getGenerationsPerService());
        int cost = generations;
        if (!request.hasReferenceImage() && isProPlusModel(request.getBaseModel())) {
            cost++;
        }
        if (!request.hasReferenceImage() && generations >= 2 && isProPlusModel(request.getVariationModel())) {
            cost++;
        }
        return cost;
    }

    private List<IconGenerationResponse.ServiceResults> mergeServiceResults(
            List<IconGenerationResponse.ServiceResults> first,
            List<IconGenerationResponse.ServiceResults> second) {
        List<IconGenerationResponse.ServiceResults> merged = new ArrayList<>(first);
        merged.addAll(second);
        return merged;
    }

    private boolean usesGpt2Model(IconGenerationRequest request) {
        if (request.hasReferenceImage()) {
            return false;
        }

        if (isProPlusModel(request.getBaseModel())) {
            return true;
        }
        return request.getGenerationsPerService() >= 2 && isProPlusModel(request.getVariationModel());
    }

    private boolean usesGptModel(IconGenerationRequest request) {
        if (request.hasReferenceImage()) {
            return false;
        }

        if (isStandardModel(request.getBaseModel())) {
            return true;
        }
        if (request.getGenerationsPerService() >= 2) {
            return isStandardModel(request.getVariationModel());
        }
        return false;
    }

    private boolean usesGpt15Model(IconGenerationRequest request) {
        if (request.hasReferenceImage()) {
            return true;
        }

        if (isProModel(request.getBaseModel())) {
            return true;
        }
        if (request.getGenerationsPerService() >= 2) {
            return isProModel(request.getVariationModel());
        }
        return false;
    }

    private boolean isStandardModel(String model) {
        return model != null && MODEL_STANDARD.equalsIgnoreCase(model.trim());
    }

    private boolean isProModel(String model) {
        return model != null && MODEL_PRO.equalsIgnoreCase(model.trim());
    }

    private boolean isProPlusModel(String model) {
        return model != null && MODEL_PRO_PLUS.equalsIgnoreCase(model.trim());
    }
}
