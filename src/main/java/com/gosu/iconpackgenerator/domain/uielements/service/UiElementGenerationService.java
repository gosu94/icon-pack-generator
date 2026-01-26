package com.gosu.iconpackgenerator.domain.uielements.service;

import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.ai.Gpt15ImageOptions;
import com.gosu.iconpackgenerator.domain.ai.Gpt15ModelService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.mockups.service.MockupImageProcessingService;
import com.gosu.iconpackgenerator.domain.uielements.dto.UiElementGenerationRequest;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.util.ErrorMessageSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class UiElementGenerationService {

    private static final String UI_ELEMENT_PROMPT =
            "Extract UI elements from this picture. If elements have labels on them, remove them. " +
            "Ensure each element has generous spacing around it for clean cropping.";

    private final Gpt15ModelService gpt15ModelService;
    private final MockupImageProcessingService mockupImageProcessingService;
    private final CoinManagementService coinManagementService;
    private final UiElementPersistenceService uiElementPersistenceService;
    private final AIServicesConfig aiServicesConfig;
    private final ErrorMessageSanitizer errorMessageSanitizer;

    public interface ProgressUpdateCallback {
        void onUpdate(ServiceProgressUpdate update);
    }

    public CompletableFuture<IconGenerationResponse> generateUiElements(
            UiElementGenerationRequest request, User user) {
        return generateUiElements(request, UUID.randomUUID().toString(), null, user);
    }

    public CompletableFuture<IconGenerationResponse> generateUiElements(
            UiElementGenerationRequest request,
            String requestId,
            ProgressUpdateCallback progressCallback,
            User user) {

        if (!aiServicesConfig.isGpt15Enabled()) {
            return CompletableFuture.completedFuture(createErrorResponse(
                    requestId, "UI element generation is unavailable because the service is disabled."));
        }

        int cost = 1;
        CoinManagementService.CoinDeductionResult coinResult =
                coinManagementService.deductCoinsForGeneration(user, cost);
        if (!coinResult.isSuccess()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, coinResult.getErrorMessage()));
        }

        final boolean isTrialMode = coinResult.isUsedTrialCoins();
        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();

        if (progressCallback != null) {
            progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "gpt15-gen1", 1));
        }

        return generateUiElementsInternal(request, seed)
                .thenApply(result -> {
                    IconGenerationResponse response = createSuccessResponse(requestId, result, seed, isTrialMode);
                    if ("success".equals(response.getStatus())) {
                        try {
                            uiElementPersistenceService.persistGeneratedUiElements(requestId, request, response, user);
                        } catch (Exception e) {
                            log.error("Error persisting UI elements for request {}", requestId, e);
                        }
                    }

                    if (progressCallback != null) {
                        ServiceProgressUpdate serviceUpdate = ServiceProgressUpdate.serviceCompleted(
                                requestId,
                                "gpt15-gen1",
                                response.getIcons(),
                                result.originalImageBase64(),
                                result.generationTimeMs(),
                                1);
                        serviceUpdate.setTrialMode(isTrialMode);
                        progressCallback.onUpdate(serviceUpdate);
                        ServiceProgressUpdate completeUpdate =
                                ServiceProgressUpdate.allCompleteWithIcons(requestId, response.getMessage(), response.getIcons());
                        completeUpdate.setTrialMode(isTrialMode);
                        progressCallback.onUpdate(completeUpdate);
                    }

                    return response;
                })
                .exceptionally(error -> {
                    log.error("Error generating UI elements for request {}", requestId, error);
                    try {
                        coinManagementService.refundCoins(user, cost, isTrialMode);
                    } catch (Exception refundError) {
                        log.error("Failed to refund coins for UI element request {}", requestId, refundError);
                    }
                    String sanitized = errorMessageSanitizer.sanitizeErrorMessage(
                            error.getMessage(), "GPT1.5");
                    return createErrorResponse(requestId, sanitized);
                });
    }

    private CompletableFuture<UiElementGenerationResult> generateUiElementsInternal(
            UiElementGenerationRequest request,
            Long seed) {
        byte[] sourceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());
        Gpt15ImageOptions options = Gpt15ImageOptions.builder()
                .background("transparent")
                .imageSize("1536x1024")
                .quality("high")
                .outputFormat("png")
                .inputFidelity("high")
                .build();

        long startTime = System.currentTimeMillis();
        return gpt15ModelService.generateImageToImage(UI_ELEMENT_PROMPT, sourceImageData, seed, options)
                .thenApply(outputImageData -> {
                    long generationTimeMs = System.currentTimeMillis() - startTime;
                    BufferedImage outputImage = readImage(outputImageData);
                    List<BufferedImage> components = mockupImageProcessingService.extractComponentsFromMockup(outputImage);
                    if (components.isEmpty()) {
                        throw new RuntimeException("No UI elements detected in the generated image.");
                    }

                    List<IconGenerationResponse.GeneratedIcon> uiElements = new ArrayList<>();
                    int index = 0;
                    for (BufferedImage component : components) {
                        String base64 = bufferedImageToBase64(component);
                        IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
                        icon.setId(UUID.randomUUID().toString());
                        icon.setBase64Data(base64);
                        icon.setDescription("UI element " + (index + 1));
                        icon.setGridPosition(index);
                        icon.setServiceSource("gpt15");
                        uiElements.add(icon);
                        index++;
                    }

                    String originalBase64 = Base64.getEncoder().encodeToString(outputImageData);
                    return new UiElementGenerationResult(uiElements, originalBase64, generationTimeMs);
                });
    }

    private IconGenerationResponse createSuccessResponse(String requestId,
                                                         UiElementGenerationResult result,
                                                         Long seed,
                                                         boolean isTrialMode) {
        IconGenerationResponse response = new IconGenerationResponse();
        response.setStatus("success");
        response.setMessage("UI elements generated successfully");
        response.setIcons(result.icons());
        response.setRequestId(requestId);
        response.setSeed(seed);
        response.setTrialMode(isTrialMode);

        IconGenerationResponse.ServiceResults serviceResults = new IconGenerationResponse.ServiceResults();
        serviceResults.setServiceName("gpt15");
        serviceResults.setStatus("success");
        serviceResults.setMessage("UI elements generated successfully");
        serviceResults.setIcons(result.icons());
        serviceResults.setGenerationTimeMs(result.generationTimeMs());
        serviceResults.setGenerationIndex(1);
        serviceResults.setOriginalGridImageBase64(result.originalImageBase64());
        response.setGpt15Results(List.of(serviceResults));

        return response;
    }

    private IconGenerationResponse createErrorResponse(String requestId, String message) {
        IconGenerationResponse response = new IconGenerationResponse();
        response.setStatus("error");
        response.setMessage(message);
        response.setIcons(new ArrayList<>());
        response.setRequestId(requestId);
        return response;
    }

    private long generateRandomSeed() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }

    private BufferedImage readImage(byte[] imageData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                throw new RuntimeException("Unable to decode generated UI image.");
            }
            return image;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse generated UI image.", e);
        }
    }

    private String bufferedImageToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode UI element image.", e);
        }
    }

    private record UiElementGenerationResult(List<IconGenerationResponse.GeneratedIcon> icons,
                                             String originalImageBase64,
                                             long generationTimeMs) {
    }
}
