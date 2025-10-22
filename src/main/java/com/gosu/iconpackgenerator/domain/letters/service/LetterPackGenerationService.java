package com.gosu.iconpackgenerator.domain.letters.service;

import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.icons.service.ImageProcessingService;
import com.gosu.iconpackgenerator.domain.icons.service.PromptGenerationService;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationRequest;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationResponse;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationResponse.LetterGroup;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationResponse.LetterIcon;
import com.gosu.iconpackgenerator.domain.ai.GptModelService;
import com.gosu.iconpackgenerator.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class LetterPackGenerationService {

    private final GptModelService gptModelService;
    private final ImageProcessingService imageProcessingService;
    private final PromptGenerationService promptGenerationService;
    private final CoinManagementService coinManagementService;
    private final LetterPackPersistenceService letterPackPersistenceService;
    private final StreamingStateStore streamingStateStore;

    private static final int LETTER_PACK_COST = 3;

    public CompletableFuture<LetterPackGenerationResponse> generateLetterPack(LetterPackGenerationRequest request, User user) {
        String requestId = UUID.randomUUID().toString();
        Long seed = request.getSeed() != null ? request.getSeed() : ThreadLocalRandom.current().nextLong();

        CoinManagementService.CoinDeductionResult coinResult = coinManagementService.deductCoinsForGeneration(user, LETTER_PACK_COST);
        if (!coinResult.isSuccess()) {
            return CompletableFuture.completedFuture(buildErrorResponse(requestId, coinResult.getErrorMessage()));
        }

        List<LetterBatch> batches = buildLetterBatches();
        String themedDescription = buildThemedDescription(request.getGeneralDescription());
        String persistedTheme = (request.getGeneralDescription() != null && !request.getGeneralDescription().trim().isEmpty())
                ? request.getGeneralDescription().trim()
                : "Letter Pack";

        CompletableFuture<LetterPackGenerationResponse> generationFuture =
                generateFirstBatch(request, batches.get(0), themedDescription, seed)
                        .thenCompose(firstBatchResult -> generateRemainingBatches(firstBatchResult, batches, themedDescription, seed))
                        .thenApply(batchResults -> buildSuccessResponse(requestId, batchResults))
                        .whenComplete((response, error) -> {
                            if (error != null) {
                                log.error("Letter pack generation failed for request {}", requestId, error);
                            }
                        });

        return generationFuture.handle((response, throwable) -> {
            if (throwable != null) {
                log.error("Unhandled error during letter pack generation for {}", requestId, throwable);
                coinManagementService.refundCoins(user, coinResult.getDeductedAmount(), coinResult.isUsedTrialCoins());
                return buildErrorResponse(requestId, "Failed to generate letter pack: " + throwable.getMessage());
            }

            if (response == null || !"success".equalsIgnoreCase(response.getStatus())) {
                coinManagementService.refundCoins(user, coinResult.getDeductedAmount(), coinResult.isUsedTrialCoins());
                return response != null ? response : buildErrorResponse(requestId, "Failed to generate letter pack");
            }

            try {
                letterPackPersistenceService.persistLetterPack(requestId, response, user, persistedTheme);
            } catch (Exception e) {
                log.error("Failed to persist letter pack for request {}", requestId, e);
            }

            streamingStateStore.addResponse(requestId, response);
            return response;
        });
    }

    private CompletableFuture<List<GeneratedBatch>> generateRemainingBatches(GeneratedBatch firstBatchResult,
                                                                             List<LetterBatch> batches,
                                                                             String themedDescription,
                                                                             Long seed) {
        byte[] referenceImage = firstBatchResult.originalImage();
        LetterBatch secondBatch = batches.get(1);
        LetterBatch thirdBatch = batches.get(2);

        CompletableFuture<GeneratedBatch> secondFuture =
                generateImageToImageBatch(secondBatch, themedDescription, referenceImage, seed);

        CompletableFuture<GeneratedBatch> thirdFuture =
                generateImageToImageBatch(thirdBatch, themedDescription, referenceImage, seed);

        return CompletableFuture.allOf(secondFuture, thirdFuture)
                .thenApply(v -> {
                    List<GeneratedBatch> results = new ArrayList<>();
                    results.add(firstBatchResult);
                    results.add(secondFuture.join());
                    results.add(thirdFuture.join());
                    return results;
                });
    }

    private CompletableFuture<GeneratedBatch> generateFirstBatch(LetterPackGenerationRequest request,
                                                                 LetterBatch batch,
                                                                 String themedDescription,
                                                                 Long seed) {
        String prompt;
        CompletableFuture<byte[]> imageFuture;

        if (request.hasReferenceImage()) {
            prompt = promptGenerationService.generatePromptForReferenceImage(
                    batch.instructions().stream().map(LetterInstruction::prompt).toList(),
                    themedDescription
            );
            byte[] referenceData = Base64.getDecoder().decode(request.getReferenceImageBase64());
            imageFuture = gptModelService.generateImageToImage(prompt, referenceData, seed);
        } else {
            prompt = promptGenerationService.generatePromptFor3x3Grid(
                    themedDescription,
                    batch.instructions().stream().map(LetterInstruction::prompt).toList()
            );
            imageFuture = gptModelService.generateImage(prompt, seed);
        }

        log.info("Starting first letter batch generation with prompt: {}", truncatePrompt(prompt));

        return imageFuture.thenApply(imageData -> processGeneratedImage(batch, imageData));
    }

    private CompletableFuture<GeneratedBatch> generateImageToImageBatch(LetterBatch batch,
                                                                        String themedDescription,
                                                                        byte[] referenceImage,
                                                                        Long seed) {
        String prompt = promptGenerationService.generatePromptForReferenceImage(
                batch.instructions().stream().map(LetterInstruction::prompt).toList(),
                themedDescription
        );

        log.info("Starting follow-up letter batch with prompt: {}", truncatePrompt(prompt));

        return gptModelService.generateImageToImage(prompt, referenceImage, seed)
                .thenApply(imageData -> processGeneratedImage(batch, imageData));
    }

    private GeneratedBatch processGeneratedImage(LetterBatch batch, byte[] imageData) {
        List<String> croppedIcons = imageProcessingService.cropIconsFromGrid(imageData, 9, false);
        String originalGridBase64 = Base64.getEncoder().encodeToString(imageData);
        return new GeneratedBatch(batch, croppedIcons, imageData, originalGridBase64);
    }

    private LetterPackGenerationResponse buildSuccessResponse(String requestId, List<GeneratedBatch> batchResults) {
        LetterPackGenerationResponse response = new LetterPackGenerationResponse();
        response.setStatus("success");
        response.setMessage("Letter pack generated successfully");
        response.setRequestId(requestId);

        LetterGroup combinedGroup = new LetterGroup();
        combinedGroup.setName("letters");
        if (!batchResults.isEmpty()) {
            combinedGroup.setOriginalGridImageBase64(batchResults.get(0).originalGridBase64());
        }

        int sequence = 0;
        for (GeneratedBatch batch : batchResults) {
            List<LetterInstruction> instructions = batch.batch().instructions();
            int limit = Math.min(instructions.size(), batch.croppedIcons().size());
            for (int i = 0; i < limit; i++) {
                LetterInstruction instruction = instructions.get(i);
                String base64 = batch.croppedIcons().get(i);

                LetterIcon icon = new LetterIcon();
                icon.setLetter(instruction.letter());
                icon.setBase64Data(base64);
                icon.setSequence(sequence++);
                combinedGroup.getIcons().add(icon);
            }
        }

        response.getGroups().add(combinedGroup);
        return response;
    }

    private LetterPackGenerationResponse buildErrorResponse(String requestId, String message) {
        LetterPackGenerationResponse response = new LetterPackGenerationResponse();
        response.setStatus("error");
        response.setMessage(message != null ? message : "Failed to generate letter pack");
        response.setRequestId(requestId);
        return response;
    }

    private String buildThemedDescription(String originalDescription) {
        String base = (originalDescription != null && !originalDescription.trim().isEmpty())
                ? originalDescription.trim()
                : "Stylized alphabet";

        if (base.toLowerCase().contains("letters of alphabet")) {
            return base + ". Each letter must be presented on a transparent background with no squares, boxes, frames, or solid color fills behind it.";
        }
        return base + " - Letters of alphabet. Each letter must be presented on a transparent background with no squares, boxes, frames, or solid color fills behind it.";
    }

    private List<LetterBatch> buildLetterBatches() {
        List<LetterBatch> batches = new ArrayList<>();

        batches.add(new LetterBatch(
                "letters-a-i",
                List.of(
                        new LetterInstruction("A", "letter A"),
                        new LetterInstruction("B", "letter B"),
                        new LetterInstruction("C", "letter C"),
                        new LetterInstruction("D", "letter D"),
                        new LetterInstruction("E", "letter E"),
                        new LetterInstruction("F", "letter F"),
                        new LetterInstruction("G", "letter G"),
                        new LetterInstruction("H", "letter H"),
                        new LetterInstruction("I", "letter I")
                )
        ));

        batches.add(new LetterBatch(
                "letters-j-s",
                List.of(
                        new LetterInstruction("J", "letter J"),
                        new LetterInstruction("K", "letter K"),
                        new LetterInstruction("L", "letter L"),
                        new LetterInstruction("M", "letter M"),
                        new LetterInstruction("N", "letter N"),
                        new LetterInstruction("O", "letter O"),
                        new LetterInstruction("P", "letter P"),
                        new LetterInstruction("R", "letter R"),
                        new LetterInstruction("S", "letter S")
                )
        ));

        batches.add(new LetterBatch(
                "letters-t-z",
                List.of(
                        new LetterInstruction("T", "letter T"),
                        new LetterInstruction("U", "letter U"),
                        new LetterInstruction("V", "letter V"),
                        new LetterInstruction("W", "letter W"),
                        new LetterInstruction("X", "letter X"),
                        new LetterInstruction("Y", "letter Y"),
                        new LetterInstruction("Z", "letter Z")
                )
        ));

        return batches;
    }

    private String truncatePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.length() > 180 ? prompt.substring(0, 180) + "..." : prompt;
    }

    private record LetterInstruction(String letter, String prompt) {
    }

    private record LetterBatch(String name, List<LetterInstruction> instructions) {
    }

    private record GeneratedBatch(LetterBatch batch,
                                  List<String> croppedIcons,
                                  byte[] originalImage,
                                  String originalGridBase64) {
    }
}
