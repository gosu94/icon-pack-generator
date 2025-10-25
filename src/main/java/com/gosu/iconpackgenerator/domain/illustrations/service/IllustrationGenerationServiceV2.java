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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Version 2 of Illustration Generation Service
 * <p>
 * This version generates illustrations individually instead of as a 2x2 grid:
 * - First illustration: text-to-image based on general theme + first description
 * - Next 3 illustrations: image-to-image in parallel, matching the style of the first
 * - No cropping or upscaling needed since illustrations are generated individually
 * - Uses LLM to generate missing individual descriptions when needed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IllustrationGenerationServiceV2 {

    private final BananaModelService bananaModelService;
    private final IllustrationPromptGenerationService illustrationPromptGenerationService;
    private final IllustrationDescriptionGenerationService illustrationDescriptionGenerationService;
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

        log.info("V2 Illustration Generation - Attempting to deduct {} coin(s) for user: {}", cost, user.getEmail());

        // Deduct coins
        CoinManagementService.CoinDeductionResult coinResult =
                coinManagementService.deductCoinsForGeneration(user, cost);

        if (!coinResult.isSuccess()) {
            log.error("V2 Illustration Generation - Coin deduction FAILED for user {}: {}", 
                    user.getEmail(), coinResult.getErrorMessage());
            return CompletableFuture.completedFuture(createErrorResponse(requestId, coinResult.getErrorMessage()));
        }

        final boolean isTrialMode = coinResult.isUsedTrialCoins();
        log.info("V2 Illustration Generation - Coin deduction SUCCESSFUL for user {}. Deducted: {} {}, Trial mode: {}", 
                user.getEmail(), coinResult.getDeductedAmount(), isTrialMode ? "trial coins" : "regular coins", isTrialMode);

        // Generate or use provided seed
        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();

        log.info("Starting V2 illustration generation for {} illustrations with theme: {} (seed: {}, trial mode: {})",
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
                log.info("Applying trial mode limitations to V2 illustration response for request {}", requestId);
                illustrationTrialModeService.applyTrialLimitations(finalResponse);
            }

            // Persist generated illustrations to database and file system
            if ("success".equals(finalResponse.getStatus())) {
                try {
                    illustrationPersistenceService.persistGeneratedIllustrations(
                            requestId, request, finalResponse, user);
                    log.info("Successfully persisted {} V2 illustrations for request {} (trial mode: {})",
                            finalResponse.getIllustrations().size(), requestId, isTrialMode);
                } catch (Exception e) {
                    log.error("Error persisting V2 illustrations for request {}", requestId, e);
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
            log.error("Error generating V2 illustrations for request {}", requestId, error);

            // Refund coins on error
            try {
                log.warn("V2 Illustration Generation - Refunding {} {} coin(s) to user {} due to error", 
                        cost, isTrialMode ? "trial" : "regular", user.getEmail());
                coinManagementService.refundCoins(user, cost, isTrialMode);
                log.info("V2 Illustration Generation - Successfully refunded {} coin(s) to user {}",
                        cost, user.getEmail());
            } catch (Exception refundException) {
                log.error("V2 Illustration Generation - FAILED to refund coins to user {}", 
                        user.getEmail(), refundException);
            }

            // Sanitize error message for user display
            String sanitizedError = sanitizeErrorMessage(error);
            return createErrorResponse(requestId, sanitizedError);
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
        log.info("Generating {} generations with Banana V2 approach", generationsCount);

        List<CompletableFuture<IllustrationGenerationResponse.ServiceResults>> generationFutures = new ArrayList<>();

        for (int i = 0; i < generationsCount; i++) {
            Long generationSeed = baseSeed + i;
            final int generationIndex = i + 1;

            CompletableFuture<IllustrationGenerationResponse.ServiceResults> generationFuture =
                    generateIllustrationsWithBananaV2(request, requestId, generationSeed, progressCallback, generationIndex)
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
     * Helper class to hold V2 generation results including reference image
     */
    private static class V2GenerationResult {
        private final List<IllustrationGenerationResponse.GeneratedIllustration> illustrations;
        private final String referenceImageBase64; // First illustration, used for "Generate More"

        public V2GenerationResult(
                List<IllustrationGenerationResponse.GeneratedIllustration> illustrations,
                String referenceImageBase64) {
            this.illustrations = illustrations;
            this.referenceImageBase64 = referenceImageBase64;
        }

        public List<IllustrationGenerationResponse.GeneratedIllustration> getIllustrations() {
            return illustrations;
        }

        public String getReferenceImageBase64() {
            return referenceImageBase64;
        }
    }

    /**
     * Generate illustrations with Banana service using V2 approach (individual generation)
     */
    private CompletableFuture<IllustrationGenerationResponse.ServiceResults>
    generateIllustrationsWithBananaV2(
            IllustrationGenerationRequest request, String requestId, Long seed,
            ProgressUpdateCallback progressCallback, int generationIndex) {

        long startTime = System.currentTimeMillis();

        return generateIllustrationsInternalV2(request, seed, progressCallback, requestId, generationIndex)
                .thenApply(v2Result -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    IllustrationGenerationResponse.ServiceResults result =
                            new IllustrationGenerationResponse.ServiceResults();
                    result.setServiceName("banana");
                    result.setStatus("success");
                    result.setMessage("Illustrations generated successfully (V2)");
                    result.setIllustrations(v2Result.getIllustrations());
                    // Use first illustration as reference for "Generate More"
                    String referenceImage = v2Result.getReferenceImageBase64();
                    log.info("Setting originalGridImageBase64 for generation {}: {} characters",
                            generationIndex, referenceImage != null ? referenceImage.length() : "null");
                    result.setOriginalGridImageBase64(referenceImage);
                    result.setGenerationTimeMs(generationTime);
                    return result;
                })
                .exceptionally(error -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    log.error("Error generating V2 illustrations with Banana", error);

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
     * Internal illustration generation logic using V2 approach
     */
    private CompletableFuture<V2GenerationResult>
    generateIllustrationsInternalV2(
            IllustrationGenerationRequest request, Long seed,
            ProgressUpdateCallback progressCallback, String requestId, int generationIndex) {

        String generalTheme = request.getGeneralDescription();
        List<String> providedDescriptions = request.getIndividualDescriptions();
        boolean hasIndividualDescriptions = providedDescriptions != null &&
                providedDescriptions.stream().anyMatch(desc -> desc != null && !desc.trim().isEmpty());

        CompletableFuture<List<String>> descriptionsFuture;
        if (request.hasReferenceImage() && !hasIndividualDescriptions) {
            log.info("Reference image provided with no individual descriptions; using generic prompts.");
            descriptionsFuture = CompletableFuture.completedFuture(
                    new ArrayList<>(Collections.nCopies(4, "")));
        } else {
            // Step 1: Ensure we have 4 individual descriptions
            descriptionsFuture = illustrationDescriptionGenerationService.ensureFourDescriptions(
                    generalTheme, providedDescriptions);
        }

        return descriptionsFuture
                .thenCompose(descriptions -> {
                    log.info("Using descriptions for V2 generation: {}", descriptions);

                    String firstDescription = descriptions.get(0);

                    // Step 2: Generate first illustration
                    // If reference image is provided, use image-to-image; otherwise use text-to-image
                    CompletableFuture<byte[]> firstIllustrationFuture;

                    if (request.hasReferenceImage()) {
                        // Use reference image for first illustration (image-to-image)
                        log.info("Generating first illustration with image-to-image from reference image");
                        String firstPrompt = illustrationPromptGenerationService
                                .generatePromptForSubsequentIndividualIllustration(generalTheme, firstDescription);
                        byte[] referenceImageData = Base64.getDecoder().decode(request.getReferenceImageBase64());
                        firstIllustrationFuture = bananaModelService.generateImageToImage(
                                firstPrompt, referenceImageData, seed, "4:3", false);
                    } else {
                        // Use text-to-image for first illustration
                        log.info("Generating first illustration with text-to-image");
                        String firstPrompt = illustrationPromptGenerationService
                                .generatePromptForFirstIndividualIllustration(generalTheme, firstDescription);
                        firstIllustrationFuture = bananaModelService.generateImage(firstPrompt, seed, "4:3", false);
                    }

                    return firstIllustrationFuture.thenCompose(firstImageData -> {
                        // Create the first illustration object
                        IllustrationGenerationResponse.GeneratedIllustration firstIllustration =
                                new IllustrationGenerationResponse.GeneratedIllustration();
                        firstIllustration.setId(UUID.randomUUID().toString());
                        firstIllustration.setBase64Data(Base64.getEncoder().encodeToString(firstImageData));
                        firstIllustration.setDescription(firstDescription);
                        firstIllustration.setGridPosition(0);
                        firstIllustration.setServiceSource("banana");

                        // Store the first illustration as reference for "Generate More" functionality
                        final String firstIllustrationBase64 = Base64.getEncoder().encodeToString(firstImageData);

                        log.info("First illustration generated, now generating remaining 3 in parallel");

                        // Step 3: Generate remaining 3 illustrations in parallel (image-to-image)
                        List<CompletableFuture<IllustrationGenerationResponse.GeneratedIllustration>> parallelFutures =
                                IntStream.range(1, 4)
                                        .mapToObj(i -> {
                                            String description = descriptions.get(i);
                                            String prompt = illustrationPromptGenerationService
                                                    .generatePromptForSubsequentIndividualIllustration(generalTheme, description);

                                            return bananaModelService.generateImageToImage(
                                                            prompt, firstImageData, seed + i, "4:3", false)
                                                    .thenApply(imageData -> {
                                                        IllustrationGenerationResponse.GeneratedIllustration illustration =
                                                                new IllustrationGenerationResponse.GeneratedIllustration();
                                                        illustration.setId(UUID.randomUUID().toString());
                                                        illustration.setBase64Data(Base64.getEncoder().encodeToString(imageData));
                                                        illustration.setDescription(description);
                                                        illustration.setGridPosition(i);
                                                        illustration.setServiceSource("banana");

                                                        log.info("Generated illustration {} of 4", i + 1);
                                                        return illustration;
                                                    });
                                        })
                                        .toList();

                        // Wait for all 3 parallel generations to complete
                        return CompletableFuture.allOf(parallelFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> {
                                    List<IllustrationGenerationResponse.GeneratedIllustration> allIllustrations =
                                            new ArrayList<>();
                                    allIllustrations.add(firstIllustration); // Add first illustration

                                    // Add the 3 parallel generations
                                    parallelFutures.forEach(future -> allIllustrations.add(future.join()));

                                    log.info("All 4 illustrations generated successfully using V2 approach");

                                    // Return V2GenerationResult with illustrations and reference image
                                    return new V2GenerationResult(allIllustrations, firstIllustrationBase64);
                                });
                    });
                });
    }

    /**
     * Generate more illustrations from an original image (for "Generate More" functionality)
     * This uses the same V2 approach: first illustration from image, then 3 more in parallel
     *
     * @param originalImageData The original/reference image data
     * @param generalTheme      The general theme/description for context (not a formatted prompt)
     * @param seed              The seed for reproducibility (should be varied from original)
     * @param descriptions      Individual descriptions for each of the 4 illustrations
     * @return CompletableFuture containing list of 4 generated illustrations
     */
    public CompletableFuture<List<IllustrationGenerationResponse.GeneratedIllustration>> generateMoreIllustrationsFromImage(
            byte[] originalImageData, String generalTheme, Long seed, List<String> descriptions) {

        log.info("Generating 4 more illustrations using V2 approach with image-to-image");

        // Generate prompt for first illustration
        String firstDescription = descriptions != null && !descriptions.isEmpty() ? descriptions.get(0) : "";
        String firstPrompt = illustrationPromptGenerationService
                .generatePromptForSubsequentIndividualIllustration(generalTheme, firstDescription);

        // Step 1: Generate first illustration using image-to-image with the original image
        return bananaModelService.generateImageToImage(firstPrompt, originalImageData, seed, "4:3", false)
                .thenCompose(firstImageData -> {
                    // Create the first illustration object
                    IllustrationGenerationResponse.GeneratedIllustration firstIllustration =
                            new IllustrationGenerationResponse.GeneratedIllustration();
                    firstIllustration.setId(UUID.randomUUID().toString());
                    firstIllustration.setBase64Data(Base64.getEncoder().encodeToString(firstImageData));
                    firstIllustration.setDescription(descriptions != null && descriptions.size() > 0 ?
                            descriptions.get(0) : "");
                    firstIllustration.setGridPosition(0);
                    firstIllustration.setServiceSource("banana");

                    log.info("First 'more' illustration generated, now generating remaining 3 in parallel");


                    // Step 2: Generate remaining 3 illustrations in parallel using the first as reference
                    List<CompletableFuture<IllustrationGenerationResponse.GeneratedIllustration>> parallelFutures =
                            IntStream.range(1, 4)
                                    .mapToObj(i -> {
                                        String description = descriptions != null && i < descriptions.size() ?
                                                descriptions.get(i) : "";

                                        // Use subsequent illustration prompt (style-matching)
                                        // Pass the raw generalTheme, not the formatted prompt
                                        String subsequentPrompt = illustrationPromptGenerationService
                                                .generatePromptForSubsequentIndividualIllustration(
                                                        generalTheme, // Use the raw general theme
                                                        description);

                                        return bananaModelService.generateImageToImage(
                                                        subsequentPrompt, firstImageData, seed + i, "4:3", false)
                                                .thenApply(imageData -> {
                                                    IllustrationGenerationResponse.GeneratedIllustration illustration =
                                                            new IllustrationGenerationResponse.GeneratedIllustration();
                                                    illustration.setId(UUID.randomUUID().toString());
                                                    illustration.setBase64Data(Base64.getEncoder().encodeToString(imageData));
                                                    illustration.setDescription(description);
                                                    illustration.setGridPosition(i);
                                                    illustration.setServiceSource("banana");

                                                    log.info("Generated 'more' illustration {} of 4", i + 1);
                                                    return illustration;
                                                });
                                    })
                                    .toList();

                    // Wait for all 3 parallel generations to complete
                    return CompletableFuture.allOf(parallelFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> {
                                List<IllustrationGenerationResponse.GeneratedIllustration> allIllustrations =
                                        new ArrayList<>();
                                allIllustrations.add(firstIllustration); // Add first illustration

                                // Add the 3 parallel generations
                                parallelFutures.forEach(future -> allIllustrations.add(future.join()));

                                log.info("All 4 'more' illustrations generated successfully using V2 approach");
                                return allIllustrations;
                            });
                });
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
                response.setMessage("All generations completed successfully (V2)");
            } else {
                response.setMessage(String.format("Generated %d successful generation(s) (V2)", successCount));
            }
        } else {
            response.setStatus("error");
            response.setMessage("Failed to generate illustrations (V2)");
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
        return "Failed to generate illustrations (V2). Please try again or contact support if the issue persists.";
    }
}
