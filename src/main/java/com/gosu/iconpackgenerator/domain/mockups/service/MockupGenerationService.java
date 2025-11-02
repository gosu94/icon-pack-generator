package com.gosu.iconpackgenerator.domain.mockups.service;

import com.gosu.iconpackgenerator.domain.ai.GptModelService;
import com.gosu.iconpackgenerator.domain.ai.GptModelService.ImageResult;
import com.gosu.iconpackgenerator.domain.ai.GptModelService.PromptAugmentation;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse.GeneratedMockup;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse.MockupComponent;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse.ServiceResults;
import com.gosu.iconpackgenerator.user.model.User;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockupGenerationService {

    private final GptModelService gptModelService;
    private final MockupImageProcessingService mockupImageProcessingService;
    private final MockupPromptGenerationService mockupPromptGenerationService;
    private final CoinManagementService coinManagementService;
    private final MockupPersistenceService mockupPersistenceService;

    private static final List<String> PRIMARY_COMPONENT_LABELS = List.of(
            "rectangle_button",
            "rectangle_button_pressed",
            "text_field_empty",
            "text_field_focused",
            "dropdown_closed",
            "dropdown_open",
            "checkbox_unchecked",
            "checkbox_checked"
    );

    private static final List<String> SECONDARY_COMPONENT_LABELS = List.of(
            "progress_bar_empty",
            "progress_bar_fill",
            "slider_track_empty",
            "slider_track_fill",
            "slider_knob",
            "vertical_scrollbar_thumb"
    );

    public interface ProgressUpdateCallback {
        void onUpdate(ServiceProgressUpdate update);
    }

    public CompletableFuture<MockupGenerationResponse> generateMockups(
            MockupGenerationRequest request, User user) {
        return generateMockups(request, UUID.randomUUID().toString(), null, user);
    }

    /**
     * Generate mockups with optional progress callback for real-time updates.
     */
    public CompletableFuture<MockupGenerationResponse> generateMockups(
            MockupGenerationRequest request,
            String requestId,
            ProgressUpdateCallback progressCallback,
            User user) {

        int cost = 2;

        CoinManagementService.CoinDeductionResult coinResult =
                coinManagementService.deductCoinsForGeneration(user, cost);

        if (!coinResult.isSuccess()) {
            return CompletableFuture.completedFuture(createErrorResponse(requestId, coinResult.getErrorMessage()));
        }

        final boolean isTrialMode = coinResult.isUsedTrialCoins();

        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();

        log.info("Starting mockup generation with description: {} (seed: {}, trial mode: {})",
                request.getDescription(), seed, isTrialMode);

        if (progressCallback != null) {
            progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, STREAM_SERVICE_KEY, 1));
        }

        return generateMockupsWithGpt(request, requestId, seed, progressCallback)
                .thenApply(serviceResults -> {
                    MockupGenerationResponse response = createCombinedResponse(requestId, serviceResults, seed);

                    if ("success".equals(response.getStatus())) {
                        try {
                            mockupPersistenceService.persistGeneratedMockups(
                                    requestId, request, response, user);
                            log.info("Persisted {} mockups for request {}", response.getMockups().size(), requestId);
                        } catch (Exception e) {
                            log.error("Error persisting mockups for request {}", requestId, e);
                        }
                    }

                    if (progressCallback != null) {
                        progressCallback.onUpdate(ServiceProgressUpdate.allCompleteWithIcons(
                                requestId,
                                response.getMessage(),
                                convertComponentsToIconList(response.getComponents())
                        ));
                    }

                    return response;
                })
                .exceptionally(error -> {
                    log.error("Error generating mockups for request {}", requestId, error);

                    try {
                        coinManagementService.refundCoins(user, cost, isTrialMode);
                        log.info("Refunded {} coin(s) to user {} due to mockup generation error",
                                cost, user.getEmail());
                    } catch (Exception refundException) {
                        log.error("Failed to refund coins to user {}", user.getEmail(), refundException);
                    }

                    String sanitizedError = sanitizeErrorMessage(error);
                    return createErrorResponse(requestId, sanitizedError);
                });
    }

    private static final String STREAM_SERVICE_KEY = "gpt-gen1";

    private CompletableFuture<List<ServiceResults>> generateMockupsWithGpt(
            MockupGenerationRequest request,
            String requestId,
            Long baseSeed,
            ProgressUpdateCallback progressCallback) {

        long startTime = System.currentTimeMillis();

        return generatePrimaryMockup(request, requestId, baseSeed)
                .thenCompose(primaryData -> generateSecondaryMockup(request, requestId, baseSeed + 1, primaryData)
                        .thenApply(secondaryData -> buildCombinedServiceResults(
                                requestId,
                                progressCallback,
                                startTime,
                                primaryData,
                                secondaryData
                        )))
                .exceptionally(error -> {
                    if (progressCallback != null) {
                        progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                                requestId,
                                STREAM_SERVICE_KEY,
                                sanitizeErrorMessage(error),
                                System.currentTimeMillis() - startTime,
                                1
                        ));
                    }
                    if (error instanceof CompletionException completionException) {
                        throw completionException;
                    }
                    throw new CompletionException(error);
                });
    }

    private CompletableFuture<GeneratedMockupData> generatePrimaryMockup(
            MockupGenerationRequest request,
            String requestId,
            Long seed) {

        String prompt;
        if (request.hasReferenceImage()) {
            prompt = mockupPromptGenerationService.generatePrimaryUiElementsPromptForReference();
        } else {
            prompt = mockupPromptGenerationService.generatePrimaryUiElementsPrompt(request.getDescription());
        }

        CompletableFuture<GptModelService.ImageResult> imageFuture;
        if (request.hasReferenceImage()) {
            byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());
            imageFuture = gptModelService.generateImageToImageWithMetadata(prompt, referenceImageData, seed);
        } else {
            imageFuture = gptModelService.generateImageWithMetadata(prompt, seed, PromptAugmentation.NONE);
        }

        return imageFuture.thenCompose(result -> {
            if (result.getImageUrl() != null && !result.getImageUrl().isBlank()) {
                log.info("GPT primary image URL for request {} (seed {}): {}", requestId, seed, result.getImageUrl());
            } else {
                log.info("GPT primary image for request {} (seed {}) returned inline data with no URL", requestId, seed);
            }
            return processMockupImage(request, result.getImageData(), 1, PRIMARY_COMPONENT_LABELS);
        });
    }

    private CompletableFuture<GeneratedMockupData> generateSecondaryMockup(
            MockupGenerationRequest request,
            String requestId,
            Long seed,
            GeneratedMockupData primaryData) {

        String prompt = mockupPromptGenerationService.generateSecondaryUiElementsPrompt(request.getDescription());
        byte[] referenceImageData = primaryData.getOriginalImageData();

        return gptModelService.generateImageToImageWithMetadata(prompt, referenceImageData, seed)
                .thenCompose(result -> {
                    if (result.getImageUrl() != null && !result.getImageUrl().isBlank()) {
                        log.info("GPT secondary image URL for request {} (seed {}): {}", requestId, seed, result.getImageUrl());
                    } else {
                        log.info("GPT secondary image for request {} (seed {}) returned inline data with no URL", requestId, seed);
                    }
                    return processMockupImage(request, result.getImageData(), 2, SECONDARY_COMPONENT_LABELS);
                });
    }

    private List<ServiceResults> buildCombinedServiceResults(
            String requestId,
            ProgressUpdateCallback progressCallback,
            long startTime,
            GeneratedMockupData primaryData,
            GeneratedMockupData secondaryData) {

        long totalDuration = System.currentTimeMillis() - startTime;

        List<GeneratedMockup> mockups = new ArrayList<>();
        mockups.add(primaryData.getMockup());
        mockups.add(secondaryData.getMockup());

        List<MockupComponent> combinedComponents = new ArrayList<>();
        combinedComponents.addAll(primaryData.getMockup().getComponents());
        combinedComponents.addAll(secondaryData.getMockup().getComponents());

        ServiceResults result = new ServiceResults();
        result.setServiceName("gpt");
        result.setStatus("success");
        result.setMessage("Mockup components generated successfully");
        result.setGenerationIndex(1);
        result.setGenerationTimeMs(totalDuration);
        result.setOriginalImageBase64(null);
        result.setMockups(mockups);
        result.setComponents(combinedComponents);

        if (progressCallback != null) {
            progressCallback.onUpdate(ServiceProgressUpdate.serviceCompletedWithComponents(
                    requestId,
                    STREAM_SERVICE_KEY,
                    convertComponentsToIconList(combinedComponents),
                    convertComponentsToIconList(combinedComponents),
                    null,
                    totalDuration,
                    1
            ));
        }

        return List.of(result);
    }

    private CompletableFuture<GeneratedMockupData> processMockupImage(
            MockupGenerationRequest request,
            byte[] originalImageData,
            int generationIndex,
            List<String> componentLabels) {

        return CompletableFuture.supplyAsync(() -> buildMockupData(
                request,
                originalImageData,
                originalImageData,
                generationIndex,
                componentLabels
        ));
    }

    private GeneratedMockupData buildMockupData(
            MockupGenerationRequest request,
            byte[] originalImageData,
            byte[] upscaledImageData,
            int generationIndex,
            List<String> componentLabels) {

        try {
            String base64Mockup = mockupImageProcessingService.processMockupImage(upscaledImageData);

            GeneratedMockup mockup = new GeneratedMockup();
            mockup.setId(UUID.randomUUID().toString());
            mockup.setBase64Data(base64Mockup);
            mockup.setDescription(request.getDescription());
            mockup.setServiceSource("gpt");
            mockup.setGenerationIndex(generationIndex);

            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(upscaledImageData));
            int orderOffset = generationIndex == 1 ? 0 : PRIMARY_COMPONENT_LABELS.size();
            List<MockupComponent> components = extractComponents(bufferedImage, componentLabels, mockup.getId(), orderOffset);
            mockup.setComponents(components);

            String originalImageBase64 = Base64.getEncoder().encodeToString(originalImageData);

            return new GeneratedMockupData(mockup, originalImageBase64, originalImageData);
        } catch (IOException e) {
            log.error("Failed to process mockup image", e);
            throw new CompletionException(new RuntimeException("Failed to process mockup image", e));
        }
    }

    private List<MockupComponent> extractComponents(BufferedImage bufferedImage,
                                                    List<String> componentLabels,
                                                    String mockupId,
                                                    int orderOffset) {
        try {
            List<BufferedImage> componentImages = mockupImageProcessingService.extractComponentsFromMockup(bufferedImage);

            List<MockupComponent> components = new ArrayList<>();
            for (int i = 0; i < componentImages.size(); i++) {
                BufferedImage componentImage = componentImages.get(i);
                String base64 = encodeBufferedImage(componentImage);
                MockupComponent component = new MockupComponent();
                component.setId(UUID.randomUUID().toString());
                component.setBase64Data(base64);
                component.setOrder(orderOffset + i + 1);
                component.setSourceMockupId(mockupId);

                components.add(component);
            }
            return components;
        } catch (IOException e) {
            log.error("Failed to extract components from mockup", e);
            return Collections.emptyList();
        }
    }

    private String encodeBufferedImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private MockupGenerationResponse createCombinedResponse(
            String requestId,
            List<ServiceResults> serviceResults,
            Long seed) {

        MockupGenerationResponse response = new MockupGenerationResponse();
        response.setRequestId(requestId);
        response.setBananaResults(serviceResults);
        response.setSeed(seed);

        List<GeneratedMockup> allMockups = new ArrayList<>();
        List<MockupComponent> allComponents = new ArrayList<>();
        for (ServiceResults result : serviceResults) {
            if (result.getMockups() != null) {
                allMockups.addAll(result.getMockups());
            }
            if (result.getComponents() != null) {
                allComponents.addAll(result.getComponents());
            }
        }
        response.setMockups(allMockups);
        response.setComponents(allComponents);

        long successCount = serviceResults.stream()
                .filter(result -> "success".equals(result.getStatus()))
                .count();

        if (successCount > 0) {
            response.setStatus("success");
            if (successCount == serviceResults.size()) {
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

    private MockupGenerationResponse createErrorResponse(String requestId, String message) {
        MockupGenerationResponse response = new MockupGenerationResponse();
        response.setRequestId(requestId);
        response.setStatus("error");
        response.setMessage(message);
        response.setMockups(new ArrayList<>());
        response.setComponents(new ArrayList<>());

        ServiceResults errorResult = new ServiceResults();
        errorResult.setStatus("error");
        errorResult.setMessage(message);
        errorResult.setMockups(new ArrayList<>());
        errorResult.setGenerationIndex(1);
        errorResult.setServiceName("gpt");
        errorResult.setComponents(new ArrayList<>());

        response.setBananaResults(List.of(errorResult));
        return response;
    }

    private List<IconGenerationResponse.GeneratedIcon> convertComponentsToIconList(List<MockupComponent> components) {
        if (components == null) {
            return new ArrayList<>();
        }

        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            MockupComponent component = components.get(i);
            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
            icon.setId(component.getId());
            icon.setBase64Data(component.getBase64Data());
            icon.setDescription(null);
            int position = component.getOrder() != null ? component.getOrder() - 1 : i;
            icon.setGridPosition(position);
            icon.setServiceSource("gpt");
            icons.add(icon);
        }
        return icons;
    }

    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }

    private String sanitizeErrorMessage(Throwable error) {
        Throwable root = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;

        String errorMessage = root.getMessage() != null ? root.getMessage() : root.toString();

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

        return "Failed to generate mockups. Please try again or contact support if the issue persists.";
    }

    /**
     * Generate more mockups from an existing image reference.
     */
    public CompletableFuture<List<GeneratedMockup>> generateMoreMockupsFromImage(
            byte[] originalImageData, String prompt, Long seed) {

        return gptModelService.generateImageToImage(prompt, originalImageData, seed)
                .thenCompose(imageData ->
                        processMockupImage(buildDefaultRequest(), imageData, 1, PRIMARY_COMPONENT_LABELS)
                                .thenApply(result -> {
                                    List<GeneratedMockup> mockups = new ArrayList<>();
                                    mockups.add(result.getMockup());
                                    return mockups;
                                })
                );
    }

    private MockupGenerationRequest buildDefaultRequest() {
        MockupGenerationRequest request = new MockupGenerationRequest();
        request.setDescription("UI kit refresh");
        request.setGenerationsPerService(1);
        request.setMockupCount(1);
        return request;
    }

    private static class GeneratedMockupData {
        private final GeneratedMockup mockup;
        private final String originalImageBase64;
        private final byte[] originalImageData;

        public GeneratedMockupData(GeneratedMockup mockup, String originalImageBase64, byte[] originalImageData) {
            this.mockup = mockup;
            this.originalImageBase64 = originalImageBase64;
            this.originalImageData = originalImageData;
        }

        public GeneratedMockup getMockup() {
            return mockup;
        }

        public String getOriginalImageBase64() {
            return originalImageBase64;
        }

        public byte[] getOriginalImageData() {
            return originalImageData;
        }
    }
}
