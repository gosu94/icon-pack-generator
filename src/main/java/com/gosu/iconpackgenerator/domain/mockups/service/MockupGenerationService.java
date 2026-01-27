package com.gosu.iconpackgenerator.domain.mockups.service;

import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.ai.BananaModelService;
import com.gosu.iconpackgenerator.domain.ai.Gpt15ImageOptions;
import com.gosu.iconpackgenerator.domain.ai.Gpt15ModelService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse;
import com.gosu.iconpackgenerator.domain.uielements.service.UiElementPersistenceService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockupGenerationService {
    
    private final BananaModelService bananaModelService;
    private final MockupImageProcessingService mockupImageProcessingService;
    private final MockupPromptGenerationService mockupPromptGenerationService;
    private final CoinManagementService coinManagementService;
    private final MockupPersistenceService mockupPersistenceService;
    private final Gpt15ModelService gpt15ModelService;
    private final UiElementPersistenceService uiElementPersistenceService;
    private final AIServicesConfig aiServicesConfig;
    
    private static final String ASPECT_RATIO_1_1 = "1:1";
    private static final String UI_ELEMENT_PROMPT =
            "Extract UI elements from this picture. If elements have labels on them, remove them. " +
            "Ensure each element has generous spacing around it for clean cropping. " +
            "Preserve the original fill and foreground colors of components (do not make them transparent unless truly transparent).";
    private static final List<List<String>> COMPONENT_SETS = List.of(
            List.of("rectangle_button", "rectangle_button_pressed", "text_field_empty", "text_field_focused"),
            List.of("progress_bar_empty", "progress_bar_filled", "switch_on", "switch_off"),
            List.of("dropdown_menu", "dropdown_menu_open", "square_arrow_button", "square_arrow_button_pressed")
    );
    
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
        
        int cost = 1;
        
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
            progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "banana", 2));
            progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "banana", 3));
        }
        
        // Generate component mockups (3 images)
        CompletableFuture<List<MockupGenerationResponse.ServiceResults>> bananaFuture =
            generateComponentMockupsWithBanana(request, requestId, seed, progressCallback);
        
        return bananaFuture.thenCompose(bananaResults -> {
            List<byte[]> mockupImages = bananaResults.stream()
                    .map(MockupGenerationResponse.ServiceResults::getOriginalImageBase64)
                    .filter(base64 -> base64 != null && !base64.isBlank())
                    .map(base64 -> Base64.getDecoder().decode(base64))
                    .toList();

            return extractUiElementsFromMockups(mockupImages, seed)
                    .exceptionally(error -> {
                        log.error("Error extracting UI elements for request {}", requestId, error);
                        return new ArrayList<>();
                    })
                    .thenApply(elements -> {
                        MockupGenerationResponse finalResponse =
                                createCombinedResponse(requestId, bananaResults, elements, seed);

                        // Note: Trial mode limitations can be added here if needed

                        // Persist generated mockups to database and file system
                        if ("success".equals(finalResponse.getStatus())) {
                            try {
                                mockupPersistenceService.persistGeneratedMockups(
                                        requestId, request, finalResponse, user);
                                log.info("Successfully persisted {} mockups for request {} (trial mode: {})",
                                        finalResponse.getMockups().size(), requestId, isTrialMode);
                                uiElementPersistenceService.persistUiElementsForMockups(
                                        requestId, finalResponse.getElements(), user, request.getDescription());
                            } catch (Exception e) {
                                log.error("Error persisting mockups for request {}", requestId, e);
                                // Don't fail the entire request if persistence fails
                            }
                        }

                        // Send final completion update
                        if (progressCallback != null) {
                            for (MockupGenerationResponse.ServiceResults result : bananaResults) {
                                String serviceGenName = "banana-gen" + result.getGenerationIndex();
                                if ("success".equals(result.getStatus())) {
                                    progressCallback.onUpdate(ServiceProgressUpdate.serviceCompleted(
                                            requestId,
                                            serviceGenName,
                                            convertToIconList(result.getMockups()),
                                            result.getOriginalImageBase64(),
                                            result.getGenerationTimeMs(),
                                            result.getGenerationIndex()));
                                } else {
                                    progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                                            requestId,
                                            serviceGenName,
                                            result.getMessage(),
                                            result.getGenerationTimeMs(),
                                            result.getGenerationIndex()));
                                }
                            }
                            progressCallback.onUpdate(ServiceProgressUpdate.allCompleteWithIcons(
                                requestId, finalResponse.getMessage(), convertToIconList(finalResponse.getMockups())));
                        }

                        return finalResponse;
                    });
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
     * Generate three component mockups with Banana service (1:1 aspect ratio).
     */
    private CompletableFuture<List<MockupGenerationResponse.ServiceResults>>
            generateComponentMockupsWithBanana(
                MockupGenerationRequest request, String requestId,
                Long baseSeed, ProgressUpdateCallback progressCallback) {

        log.info("Generating 3 component mockups with Banana (1:1 aspect ratio)");

        String set1 = String.join(", ", COMPONENT_SETS.get(0));
        String set2 = String.join(", ", COMPONENT_SETS.get(1));
        String set3 = String.join(", ", COMPONENT_SETS.get(2));

        CompletableFuture<MockupBundle> firstFuture = generateComponentSet(
                request, requestId, baseSeed, 1, null, set1, progressCallback);

        return firstFuture.thenCompose(first -> {
            byte[] referenceImage = first.imageData();
            if (referenceImage == null || referenceImage.length == 0) {
                return CompletableFuture.failedFuture(
                        new RuntimeException("Failed to generate initial UI mockup for component sets."));
            }
            CompletableFuture<MockupGenerationResponse.ServiceResults> secondFuture =
                    generateComponentSet(request, requestId, baseSeed + 1, 2, referenceImage, set2, progressCallback)
                            .thenApply(MockupBundle::serviceResult);
            CompletableFuture<MockupGenerationResponse.ServiceResults> thirdFuture =
                    generateComponentSet(request, requestId, baseSeed + 2, 3, referenceImage, set3, progressCallback)
                            .thenApply(MockupBundle::serviceResult);

            return CompletableFuture.allOf(secondFuture, thirdFuture)
                    .thenApply(v -> List.of(first.serviceResult(), secondFuture.join(), thirdFuture.join()));
        });
    }
    
    private record MockupBundle(MockupGenerationResponse.ServiceResults serviceResult, byte[] imageData) {}

    private CompletableFuture<MockupBundle> generateComponentSet(
            MockupGenerationRequest request,
            String requestId,
            Long seed,
            int generationIndex,
            byte[] referenceImageOverride,
            String componentsCsv,
            ProgressUpdateCallback progressCallback) {

        long startTime = System.currentTimeMillis();
        byte[] referenceImage = referenceImageOverride;
        if (referenceImage == null && request.hasReferenceImage()) {
            referenceImage = Base64.getDecoder().decode(request.getReferenceImageBase64());
        }

        boolean useReference = referenceImage != null && referenceImage.length > 0;
        String safeDescription = request.getDescription();
        if (safeDescription == null || safeDescription.isBlank()) {
            safeDescription = "clean modern UI kit";
        }
        String prompt = useReference
                ? mockupPromptGenerationService.generateComponentSetPromptForReference(componentsCsv)
                : mockupPromptGenerationService.generateComponentSetPromptForText(safeDescription, componentsCsv);

        CompletableFuture<byte[]> generationFuture = useReference
                ? bananaModelService.generateImageToImage(prompt, referenceImage, seed, ASPECT_RATIO_1_1)
                : bananaModelService.generateImage(prompt, seed, ASPECT_RATIO_1_1);

        return generationFuture.thenApply(imageData -> {
            long generationTime = System.currentTimeMillis() - startTime;
            String base64Mockup = mockupImageProcessingService.processMockupImage(imageData);

            MockupGenerationResponse.GeneratedMockup mockup = new MockupGenerationResponse.GeneratedMockup();
            mockup.setId(UUID.randomUUID().toString());
            mockup.setBase64Data(base64Mockup);
            mockup.setDescription("Component set " + generationIndex);
            mockup.setServiceSource("banana");

            MockupGenerationResponse.ServiceResults result = new MockupGenerationResponse.ServiceResults();
            result.setServiceName("banana");
            result.setStatus("success");
            result.setMessage("Mockup generated successfully");
            result.setMockups(List.of(mockup));
            result.setOriginalImageBase64(Base64.getEncoder().encodeToString(imageData));
            result.setGenerationTimeMs(generationTime);
            result.setGenerationIndex(generationIndex);

            return new MockupBundle(result, imageData);
        }).whenComplete((bundle, error) -> {
            if (progressCallback == null) {
                return;
            }
            String serviceGenName = "banana-gen" + generationIndex;
            if (error != null) {
                String sanitizedError = sanitizeErrorMessage(error);
                progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                        requestId, serviceGenName, sanitizedError, 0L, generationIndex));
            } else if (bundle != null && bundle.serviceResult() != null) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceProcessing(
                        requestId,
                        serviceGenName,
                        "Mockup generated. Extracting UI elements...",
                        generationIndex));
            }
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

        return bananaModelService.generateImage(prompt, seed, ASPECT_RATIO_1_1)
                .thenApply(imageData -> {
                    String base64Mockup = mockupImageProcessingService.processMockupImage(imageData);
                    return createMockupWithOriginalImage(base64Mockup, imageData, request);
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

        return bananaModelService.generateImageToImage(prompt, referenceImageData, seed, ASPECT_RATIO_1_1)
                .thenApply(imageData -> {
                    String base64Mockup = mockupImageProcessingService.processMockupImage(imageData);
                    return createMockupWithOriginalImage(base64Mockup, imageData, request);
                });
    }
    
    /**
     * Generate more mockups from an original image with upscaling
     * This is used for the "Generate More" functionality
     */
    public CompletableFuture<List<MockupGenerationResponse.GeneratedMockup>> generateMoreMockupsFromImage(
            byte[] originalImageData, String prompt, Long seed) {

        return bananaModelService.generateImageToImage(prompt, originalImageData, seed, ASPECT_RATIO_1_1)
                .thenApply(imageData -> {
                    String base64Mockup = mockupImageProcessingService.processMockupImage(imageData);

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

    private CompletableFuture<List<IconGenerationResponse.GeneratedIcon>> extractUiElementsFromMockups(
            List<byte[]> mockupImages, Long seed) {
        if (!aiServicesConfig.isGpt15Enabled()) {
            log.warn("GPT1.5 is disabled; skipping UI element extraction.");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        if (mockupImages == null || mockupImages.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        Gpt15ImageOptions options = Gpt15ImageOptions.builder()
                .background("transparent")
                .imageSize("1536x1024")
                .quality("high")
                .outputFormat("png")
                .inputFidelity("high")
                .build();

        List<CompletableFuture<List<IconGenerationResponse.GeneratedIcon>>> elementFutures = new ArrayList<>();
        int index = 0;
        for (byte[] imageData : mockupImages) {
            Long imageSeed = seed != null ? seed + index : null;
            int batchIndex = index;
            List<String> componentSet = batchIndex < COMPONENT_SETS.size()
                    ? COMPONENT_SETS.get(batchIndex)
                    : List.of();
            String componentPrompt = buildUiElementPrompt(componentSet);
            CompletableFuture<List<IconGenerationResponse.GeneratedIcon>> future = gpt15ModelService
                    .generateImageToImage(componentPrompt, imageData, imageSeed, options)
                    .thenApply(outputImageData -> {
                        BufferedImage outputImage = readImage(outputImageData);
                        List<BufferedImage> components =
                                mockupImageProcessingService.extractComponentsFromMockup(outputImage);
                        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();
                        int componentIndex = 0;
                        for (BufferedImage component : components) {
                            String base64 = bufferedImageToBase64(component);
                            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
                            icon.setId(UUID.randomUUID().toString());
                            icon.setBase64Data(base64);
                            icon.setDescription("UI element " + (batchIndex + 1) + "-" + (componentIndex + 1));
                            icon.setGridPosition(componentIndex);
                            icon.setServiceSource("gpt15");
                            icons.add(icon);
                            componentIndex++;
                        }
                        return icons;
                    });
            elementFutures.add(future);
            index++;
        }

        return CompletableFuture.allOf(elementFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> elementFutures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .toList());
    }

    private String buildUiElementPrompt(List<String> components) {
        if (components == null || components.isEmpty()) {
            return UI_ELEMENT_PROMPT;
        }
        return UI_ELEMENT_PROMPT + " Extract only the following components: " +
                String.join(", ", components) + ".";
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
    
    /**
     * Create combined response
     */
    private MockupGenerationResponse createCombinedResponse(
            String requestId,
            List<MockupGenerationResponse.ServiceResults> bananaResults,
            List<IconGenerationResponse.GeneratedIcon> elements,
            Long seed) {
        
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
        response.setElements(elements != null ? elements : new ArrayList<>());
        
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
        response.setElements(new ArrayList<>());
        
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
