package com.gosu.iconpackgenerator.domain.labels.service;

import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.ai.GptModelService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationResponse;
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
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabelGenerationService {

    private final GptModelService gptModelService;
    private final LabelPromptGenerationService labelPromptGenerationService;
    private final LabelImageProcessingService labelImageProcessingService;
    private final LabelPersistenceService labelPersistenceService;
    private final CoinManagementService coinManagementService;
    private final AIServicesConfig aiServicesConfig;
    private final ErrorMessageSanitizer errorMessageSanitizer;

    public interface ProgressUpdateCallback {
        void onUpdate(ServiceProgressUpdate update);
    }

    public CompletableFuture<LabelGenerationResponse> generateLabels(
            LabelGenerationRequest request, User user) {
        return generateLabels(request, UUID.randomUUID().toString(), null, user);
    }

    public CompletableFuture<LabelGenerationResponse> generateLabels(
            LabelGenerationRequest request,
            String requestId,
            ProgressUpdateCallback progressCallback,
            User user) {

        if (!aiServicesConfig.isGptEnabled()) {
            return CompletableFuture.completedFuture(createErrorResponse(
                    requestId,
                    "Label generation is currently unavailable. Please try again later."
            ));
        }

        int cost = Math.max(1, request.getGenerationsPerService());
        CoinManagementService.CoinDeductionResult coinResult =
                coinManagementService.deductCoinsForGeneration(user, cost);

        if (!coinResult.isSuccess()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, coinResult.getErrorMessage()));
        }

        Long baseSeed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();
        log.info("Starting label generation for '{}' (request={}, seed={}, variations={})",
                request.getLabelText(), requestId, baseSeed, request.getGenerationsPerService());

        if (progressCallback != null) {
            for (int i = 1; i <= request.getGenerationsPerService(); i++) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "gpt", i));
            }
        }

        List<CompletableFuture<LabelGenerationResponse.ServiceResults>> generationFutures = new ArrayList<>();
        for (int i = 0; i < request.getGenerationsPerService(); i++) {
            int generationIndex = i + 1;
            Long seed = baseSeed + i;
            boolean isVariation = generationIndex == 2;

            generationFutures.add(
                    generateSingleLabel(request, requestId, generationIndex, isVariation, seed, progressCallback)
                            .thenApply(result -> {
                                result.setGenerationIndex(generationIndex);
                                return result;
                            })
            );
        }

        return CompletableFuture.allOf(generationFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<LabelGenerationResponse.ServiceResults> serviceResults = generationFutures.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    LabelGenerationResponse response = createCombinedResponse(requestId, serviceResults, baseSeed);

                    if ("success".equals(response.getStatus())) {
                        try {
                            labelPersistenceService.persistGeneratedLabels(requestId, request, response, user);
                        } catch (Exception e) {
                            log.error("Failed to persist labels for request {}", requestId, e);
                        }
                    }

                    if (progressCallback != null) {
                        progressCallback.onUpdate(ServiceProgressUpdate.allCompleteWithIcons(
                                requestId,
                                response.getMessage(),
                                convertToIconList(response.getLabels())
                        ));
                    }

                    return response;
                })
                .exceptionally(error -> {
                    log.error("Error generating labels for request {}", requestId, error);
                    try {
                        coinManagementService.refundCoins(user, cost, coinResult.isUsedTrialCoins());
                    } catch (Exception refundError) {
                        log.error("Failed to refund coins to user {} for request {}", user.getEmail(), requestId, refundError);
                    }
                    String sanitized = errorMessageSanitizer.sanitizeErrorMessage(error.getMessage(), "GPT");
                    return createErrorResponse(requestId, sanitized);
                });
    }

    private CompletableFuture<LabelGenerationResponse.ServiceResults> generateSingleLabel(
            LabelGenerationRequest request,
            String requestId,
            int generationIndex,
            boolean variation,
            Long seed,
            ProgressUpdateCallback progressCallback) {

        long startTime = System.currentTimeMillis();
        CompletableFuture<byte[]> imageFuture;

        if (request.hasReferenceImage()) {
            byte[] referenceBytes = Base64.getDecoder().decode(request.getReferenceImageBase64());
            String prompt = labelPromptGenerationService.buildImageToImagePrompt(request.getLabelText(), variation);
            imageFuture = gptModelService.generateImageToImage(prompt, referenceBytes, seed);
        } else {
            String prompt = labelPromptGenerationService.buildTextToImagePrompt(
                    request.getLabelText(),
                    request.getGeneralTheme(),
                    variation);
            imageFuture = gptModelService.generateImage(prompt, seed, GptModelService.PromptAugmentation.NONE);
        }

        return imageFuture.thenApply(imageBytes -> {
            long generationTime = System.currentTimeMillis() - startTime;
            String originalBase64 = Base64.getEncoder().encodeToString(imageBytes);
            String processedBase64 = labelImageProcessingService.cropToContent(originalBase64);

            LabelGenerationResponse.GeneratedLabel label = new LabelGenerationResponse.GeneratedLabel();
            label.setId(UUID.randomUUID().toString());
            label.setBase64Data(processedBase64);
            label.setLabelText(request.getLabelText());
            label.setServiceSource("gpt");
            label.setGenerationIndex(generationIndex);

            LabelGenerationResponse.ServiceResults result = new LabelGenerationResponse.ServiceResults();
            result.setServiceName("gpt");
            result.setStatus("success");
            result.setMessage("Label generated successfully");
            result.setLabels(List.of(label));
            result.setOriginalImageBase64(originalBase64);
            result.setGenerationTimeMs(generationTime);
            result.setGenerationIndex(generationIndex);

            if (progressCallback != null) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceCompleted(
                        requestId,
                        "gpt-gen" + generationIndex,
                        convertToIconList(result.getLabels()),
                        result.getOriginalImageBase64(),
                        result.getGenerationTimeMs(),
                        generationIndex
                ));
            }

            return result;
        }).exceptionally(error -> {
            long generationTime = System.currentTimeMillis() - startTime;
            if (progressCallback != null) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                        requestId,
                        "gpt-gen" + generationIndex,
                        sanitizeError(error),
                        generationTime,
                        generationIndex
                ));
            }
            throw new CompletionException(error);
        });
    }

    private LabelGenerationResponse createCombinedResponse(String requestId,
                                                           List<LabelGenerationResponse.ServiceResults> serviceResults,
                                                           Long seed) {
        LabelGenerationResponse response = new LabelGenerationResponse();
        response.setRequestId(requestId);
        response.setSeed(seed);
        response.setStatus("success");
        response.setMessage("Label generation completed successfully");

        List<LabelGenerationResponse.GeneratedLabel> labels = serviceResults.stream()
                .filter(result -> "success".equals(result.getStatus()))
                .flatMap(result -> result.getLabels().stream())
                .toList();

        response.setLabels(labels);
        response.setGptResults(serviceResults);

        if (labels.isEmpty()) {
            response.setStatus("error");
            response.setMessage("Label generation did not produce any results");
        }

        return response;
    }

    private LabelGenerationResponse createErrorResponse(String requestId, String message) {
        LabelGenerationResponse response = new LabelGenerationResponse();
        response.setRequestId(requestId);
        response.setStatus("error");
        response.setMessage(message);
        response.setLabels(List.of());
        response.setGptResults(List.of());
        return response;
    }

    private List<IconGenerationResponse.GeneratedIcon> convertToIconList(List<LabelGenerationResponse.GeneratedLabel> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();
        for (LabelGenerationResponse.GeneratedLabel label : labels) {
            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
            icon.setId(label.getId());
            icon.setBase64Data(label.getBase64Data());
            icon.setDescription(label.getLabelText());
            icon.setGridPosition(0);
            icon.setServiceSource(label.getServiceSource());
            icons.add(icon);
        }
        return icons;
    }

    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }

    private String sanitizeError(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error during label generation";
        }
        return errorMessageSanitizer.sanitizeErrorMessage(throwable.getMessage(), "GPT");
    }
}

