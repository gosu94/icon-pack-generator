package com.gosu.iconpackgenerator.admin.service;

import com.gosu.iconpackgenerator.admin.dto.AdminTestLabIconRequest;
import com.gosu.iconpackgenerator.admin.dto.AdminTestLabIconResponse;
import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.ai.Gpt15ModelService;
import com.gosu.iconpackgenerator.domain.ai.Gpt2ModelService;
import com.gosu.iconpackgenerator.domain.ai.GptModelService;
import com.gosu.iconpackgenerator.domain.icons.service.IconPromptEnhancementService;
import com.gosu.iconpackgenerator.domain.icons.service.ImageProcessingService;
import com.gosu.iconpackgenerator.domain.icons.service.PromptGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTestLabService {

    private static final String MODEL_GPT_1 = "gpt";
    private static final String MODEL_GPT_15 = "gpt15";
    private static final String MODEL_GPT_2 = "gpt2";

    private final GptModelService gptModelService;
    private final Gpt15ModelService gpt15ModelService;
    private final Gpt2ModelService gpt2ModelService;
    private final PromptGenerationService promptGenerationService;
    private final ImageProcessingService imageProcessingService;
    private final IconPromptEnhancementService iconPromptEnhancementService;
    private final AIServicesConfig aiServicesConfig;

    public CompletableFuture<AdminTestLabIconResponse> generateIconComparisons(AdminTestLabIconRequest request) {
        String originalGeneralDescription = request.getGeneralDescription();
        String effectiveGeneralDescription = request.isEnhancePrompt()
                ? iconPromptEnhancementService.enhanceIfPossible(request.getGeneralDescription())
                : request.getGeneralDescription();

        request.setGeneralDescription(effectiveGeneralDescription);
        List<String> normalizedDescriptions = normalizeIconDescriptions(request.getIndividualDescriptions(), request.getIconCount());
        request.setIndividualDescriptions(normalizedDescriptions);

        String promptUsed = request.hasReferenceImage()
                ? promptGenerationService.generatePromptForReferenceImage(normalizedDescriptions, effectiveGeneralDescription)
                : promptGenerationService.generatePromptFor3x3Grid(effectiveGeneralDescription, normalizedDescriptions);

        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();
        byte[] referenceImageData = request.hasReferenceImage()
                ? Base64.getDecoder().decode(request.getReferenceImageBase64())
                : null;

        List<CompletableFuture<AdminTestLabIconResponse.ModelResult>> futures = new ArrayList<>();

        if (request.isRunGpt()) {
            futures.add(runModel(MODEL_GPT_1, "GPT-1", aiServicesConfig.isGptEnabled(),
                    () -> generateWithGpt1(promptUsed, referenceImageData, seed)));
        }
        if (request.isRunGpt15()) {
            futures.add(runModel(MODEL_GPT_15, "GPT-1.5", aiServicesConfig.isGpt15Enabled(),
                    () -> generateWithGpt15(promptUsed, referenceImageData, seed)));
        }
        if (request.isRunGpt2()) {
            futures.add(runModel(MODEL_GPT_2, "GPT-2", aiServicesConfig.isGpt2Enabled(),
                    () -> generateWithGpt2(promptUsed, referenceImageData, seed)));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<AdminTestLabIconResponse.ModelResult> results = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    AdminTestLabIconResponse response = new AdminTestLabIconResponse();
                    response.setSeed(seed);
                    response.setReferenceImageMode(request.hasReferenceImage());
                    response.setPromptEnhanced(request.isEnhancePrompt());
                    response.setOriginalGeneralDescription(originalGeneralDescription);
                    response.setEffectiveGeneralDescription(effectiveGeneralDescription);
                    response.setPromptUsed(promptUsed);
                    response.setIndividualDescriptions(normalizedDescriptions);
                    response.setResults(results);

                    boolean anySuccess = results.stream().anyMatch(result -> "success".equals(result.getStatus()));
                    response.setStatus(anySuccess ? "success" : "error");
                    response.setMessage(anySuccess
                            ? "Test lab generation completed"
                            : "All model generations failed");

                    return response;
                });
    }

    private CompletableFuture<AdminTestLabIconResponse.ModelResult> runModel(
            String modelId,
            String modelLabel,
            boolean enabled,
            Supplier<CompletableFuture<byte[]>> generationSupplier) {

        if (!enabled) {
            return CompletableFuture.completedFuture(createDisabledResult(modelId, modelLabel));
        }

        long startTime = System.currentTimeMillis();
        return generationSupplier.get()
                .thenApply(imageData -> createSuccessResult(modelId, modelLabel, imageData, System.currentTimeMillis() - startTime))
                .exceptionally(error -> createErrorResult(modelId, modelLabel, error, System.currentTimeMillis() - startTime));
    }

    private CompletableFuture<byte[]> generateWithGpt1(String prompt, byte[] referenceImageData, Long seed) {
        if (referenceImageData != null) {
            return gptModelService.generateImageToImage(prompt, referenceImageData, seed);
        }
        return gptModelService.generateImage(prompt, seed);
    }

    private CompletableFuture<byte[]> generateWithGpt15(String prompt, byte[] referenceImageData, Long seed) {
        if (referenceImageData != null) {
            return gpt15ModelService.generateImageToImage(prompt, referenceImageData, seed);
        }
        return gpt15ModelService.generateImage(prompt, seed);
    }

    private CompletableFuture<byte[]> generateWithGpt2(String prompt, byte[] referenceImageData, Long seed) {
        if (referenceImageData != null) {
            return gpt2ModelService.generateImageToImage(prompt, referenceImageData, seed);
        }
        return gpt2ModelService.generateImage(prompt, seed);
    }

    private AdminTestLabIconResponse.ModelResult createSuccessResult(
            String modelId,
            String modelLabel,
            byte[] imageData,
            long generationTimeMs) {

        List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false);
        AdminTestLabIconResponse.ModelResult result = new AdminTestLabIconResponse.ModelResult();
        result.setModelId(modelId);
        result.setModelLabel(modelLabel);
        result.setStatus("success");
        result.setMessage("Icons generated successfully");
        result.setGenerationTimeMs(generationTimeMs);
        result.setOriginalGridImageBase64(Base64.getEncoder().encodeToString(imageData));
        result.setIcons(createIconList(base64Icons, modelId));
        return result;
    }

    private AdminTestLabIconResponse.ModelResult createErrorResult(
            String modelId,
            String modelLabel,
            Throwable error,
            long generationTimeMs) {

        Throwable cause = error.getCause() != null ? error.getCause() : error;
        String message = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
        log.warn("Test lab model {} failed: {}", modelId, message, cause);

        AdminTestLabIconResponse.ModelResult result = new AdminTestLabIconResponse.ModelResult();
        result.setModelId(modelId);
        result.setModelLabel(modelLabel);
        result.setStatus("error");
        result.setMessage(message);
        result.setGenerationTimeMs(generationTimeMs);
        result.setIcons(new ArrayList<>());
        return result;
    }

    private AdminTestLabIconResponse.ModelResult createDisabledResult(String modelId, String modelLabel) {
        AdminTestLabIconResponse.ModelResult result = new AdminTestLabIconResponse.ModelResult();
        result.setModelId(modelId);
        result.setModelLabel(modelLabel);
        result.setStatus("error");
        result.setMessage("This service is disabled in configuration.");
        result.setGenerationTimeMs(0);
        result.setIcons(new ArrayList<>());
        return result;
    }

    private List<AdminTestLabIconResponse.GeneratedIcon> createIconList(List<String> base64Icons, String serviceSource) {
        List<AdminTestLabIconResponse.GeneratedIcon> icons = new ArrayList<>();
        for (int i = 0; i < base64Icons.size(); i++) {
            AdminTestLabIconResponse.GeneratedIcon icon = new AdminTestLabIconResponse.GeneratedIcon();
            icon.setId(UUID.randomUUID().toString());
            icon.setBase64Data(base64Icons.get(i));
            icon.setDescription("");
            icon.setGridPosition(i);
            icon.setServiceSource(serviceSource);
            icons.add(icon);
        }
        return icons;
    }

    private List<String> normalizeIconDescriptions(List<String> descriptions, int iconCount) {
        List<String> normalized = new ArrayList<>();
        if (descriptions != null) {
            normalized.addAll(descriptions);
        }
        while (normalized.size() < iconCount) {
            normalized.add("");
        }
        if (normalized.size() > iconCount) {
            return new ArrayList<>(normalized.subList(0, iconCount));
        }
        return normalized;
    }

    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }
}
