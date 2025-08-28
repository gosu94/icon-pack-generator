package com.gosu.iconpackgenerator.domain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosu.iconpackgenerator.config.AIServicesConfig;
import com.gosu.iconpackgenerator.domain.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.controller.api.IconGenerationControllerAPI;
import com.gosu.iconpackgenerator.domain.dto.IconGenerationRequest;
import com.gosu.iconpackgenerator.domain.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.dto.MoreIconsRequest;
import com.gosu.iconpackgenerator.domain.dto.MoreIconsResponse;
import com.gosu.iconpackgenerator.domain.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.service.BananaModelService;
import com.gosu.iconpackgenerator.domain.service.DataInitializationService;
import com.gosu.iconpackgenerator.domain.service.FileStorageService;
import com.gosu.iconpackgenerator.domain.service.FluxModelService;
import com.gosu.iconpackgenerator.domain.service.GptModelService;
import com.gosu.iconpackgenerator.domain.service.IconGenerationService;
import com.gosu.iconpackgenerator.domain.service.ImageProcessingService;
import com.gosu.iconpackgenerator.domain.service.ImagenModelService;
import com.gosu.iconpackgenerator.domain.service.PhotonModelService;
import com.gosu.iconpackgenerator.domain.service.PromptGenerationService;
import com.gosu.iconpackgenerator.domain.service.RecraftModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IconGenerationController implements IconGenerationControllerAPI {

    private final IconGenerationService iconGenerationService;
    private final FluxModelService fluxModelService;
    private final RecraftModelService recraftModelService;
    private final ImagenModelService imagenModelService;
    private final PhotonModelService photonModelService;
    private final GptModelService gptModelService;
    private final BananaModelService bananaModelService;
    private final PromptGenerationService promptGenerationService;
    private final ImageProcessingService imageProcessingService;
    private final AIServicesConfig aiServicesConfig;
    private final ObjectMapper objectMapper;
    private final GeneratedIconRepository generatedIconRepository;
    private final DataInitializationService dataInitializationService;
    private final FileStorageService fileStorageService;
    private final StreamingStateStore streamingStateStore;

    @Override
    @ResponseBody
    public CompletableFuture<IconGenerationResponse> generateIcons(@Valid @RequestBody IconGenerationRequest request) {
        if (!request.isValid()) {
            throw new IllegalArgumentException("Either general description or reference image must be provided");
        }

        if (request.hasReferenceImage()) {
            log.info("Received reference image-based icon generation request for {} icons", request.getIconCount());
        } else {
            log.info("Received text-based icon generation request for {} icons with theme: {}",
                    request.getIconCount(), request.getGeneralDescription());
        }

        if (request.getIndividualDescriptions() == null) {
            request.setIndividualDescriptions(new ArrayList<>());
        }

        while (request.getIndividualDescriptions().size() < request.getIconCount()) {
            request.getIndividualDescriptions().add("");
        }

        return iconGenerationService.generateIcons(request)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.error("Error generating icons", error);
                    } else {
                        log.info("Successfully generated icons for request: {}", response.getRequestId());
                    }
                });
    }

    @Override
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startStreamingGeneration(@Valid @RequestBody IconGenerationRequest request) {
        if (!request.isValid()) {
            throw new IllegalArgumentException("Either general description or reference image must be provided");
        }

        if (request.hasReferenceImage()) {
            log.info("Starting streaming reference image-based icon generation for {} icons", request.getIconCount());
        } else {
            log.info("Starting streaming text-based icon generation for {} icons with theme: {}",
                    request.getIconCount(), request.getGeneralDescription());
        }

        String requestId = UUID.randomUUID().toString();
        streamingStateStore.addRequest(requestId, request);

        CompletableFuture.runAsync(() -> {
            processStreamingGeneration(requestId, request);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        Map<String, Boolean> enabledServices = new HashMap<>();
        enabledServices.put("flux", aiServicesConfig.isFluxAiEnabled());
        enabledServices.put("recraft", aiServicesConfig.isRecraftEnabled());
        enabledServices.put("photon", aiServicesConfig.isPhotonEnabled());
        enabledServices.put("gpt", aiServicesConfig.isGptEnabled());
        enabledServices.put("imagen", aiServicesConfig.isImagenEnabled());
        enabledServices.put("banana", aiServicesConfig.isBananaEnabled());
        response.put("enabledServices", enabledServices);

        return ResponseEntity.ok(response);
    }

    @Override
    @ResponseBody
    public SseEmitter connectToStream(@PathVariable String requestId) {
        log.info("Client connecting to stream for request: {}", requestId);

        SseEmitter emitter = new SseEmitter(300_000L);
        streamingStateStore.addEmitter(requestId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE completed for request: {}", requestId);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for request: {}", requestId);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.error("SSE error for request: {}", requestId, throwable);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.completeWithError(throwable);
        });

        return emitter;
    }

    private void processStreamingGeneration(String requestId, IconGenerationRequest request) {
        try {
            if (!request.isValid()) {
                throw new IllegalArgumentException("Either general description or reference image must be provided");
            }

            if (request.getIndividualDescriptions() == null) {
                request.setIndividualDescriptions(new ArrayList<>());
            }

            while (request.getIndividualDescriptions().size() < request.getIconCount()) {
                request.getIndividualDescriptions().add("");
            }

            iconGenerationService.generateIcons(request, requestId, update -> {
                SseEmitter emitter = streamingStateStore.getEmitter(requestId);
                if (emitter != null) {
                    try {
                        String jsonUpdate = objectMapper.writeValueAsString(update);
                        emitter.send(SseEmitter.event()
                                .name(update.getEventType())
                                .data(jsonUpdate));

                        if ("generation_complete".equals(update.getEventType())) {
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        log.error("Error sending SSE update for request: {}", requestId, e);
                        emitter.completeWithError(e);
                    }
                }
            }).whenComplete((response, error) -> {
                SseEmitter emitter = streamingStateStore.getEmitter(requestId);
                if (emitter != null && error != null) {
                    log.error("Error in streaming generation for request: {}", requestId, error);
                    try {
                        ServiceProgressUpdate errorUpdate = new ServiceProgressUpdate();
                        errorUpdate.setRequestId(requestId);
                        errorUpdate.setEventType("generation_error");
                        errorUpdate.setStatus("error");
                        errorUpdate.setMessage("Generation failed: " + error.getMessage());

                        String jsonUpdate = objectMapper.writeValueAsString(errorUpdate);
                        emitter.send(SseEmitter.event()
                                .name("generation_error")
                                .data(jsonUpdate));
                    } catch (Exception e) {
                        log.error("Error sending error update for request: {}", requestId, e);
                    }
                    emitter.completeWithError(error);
                } else if (response != null) {
                    streamingStateStore.addResponse(requestId, response);
                }
            });

        } catch (Exception e) {
            log.error("Error in processStreamingGeneration for request: {}", requestId, e);
            SseEmitter emitter = streamingStateStore.getEmitter(requestId);
            if (emitter != null) {
                emitter.completeWithError(e);
            }
        }
    }

    @Override
    @ResponseBody
    public DeferredResult<MoreIconsResponse> generateMoreIcons(@RequestBody MoreIconsRequest request) {
        log.info("Received generate more icons request for service: {} with {} icon descriptions for generation index: {}",
                request.getServiceName(),
                request.getIconDescriptions() != null ? request.getIconDescriptions().size() : 0,
                request.getGenerationIndex());

        DeferredResult<MoreIconsResponse> deferredResult = new DeferredResult<>(300000L);

        CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                if (request.getServiceName() == null || request.getServiceName().trim().isEmpty()) {
                    return createErrorResponse(request, "Service name is required", startTime);
                }

                if (request.getOriginalImageBase64() == null || request.getOriginalImageBase64().trim().isEmpty()) {
                    return createErrorResponse(request, "Original image is required", startTime);
                }

                byte[] originalImageData = Base64.getDecoder().decode(request.getOriginalImageBase64());
                String prompt = promptGenerationService.generatePromptForReferenceImage(request.getIconDescriptions(), request.getGeneralDescription());
                CompletableFuture<byte[]> generationFuture = getServiceAndGenerate(request.getServiceName(), prompt, originalImageData, request.getSeed());
                byte[] newImageData = generationFuture.join();
                List<String> base64Icons = imageProcessingService.cropIconsFromGrid(newImageData, 9, true, 0, false);
                List<IconGenerationResponse.GeneratedIcon> newIcons = createIconList(base64Icons, request);

                try {
                    persistMoreIcons(request, newIcons);
                    log.info("Successfully persisted {} more icons for request {}", newIcons.size(), request.getOriginalRequestId());
                } catch (Exception e) {
                    log.error("Error persisting more icons for request {}", request.getOriginalRequestId(), e);
                }

                MoreIconsResponse response = new MoreIconsResponse();
                response.setStatus("success");
                response.setMessage("More icons generated successfully with same style");
                response.setServiceName(request.getServiceName());
                response.setNewIcons(newIcons);
                response.setOriginalRequestId(request.getOriginalRequestId());
                response.setGenerationTimeMs(System.currentTimeMillis() - startTime);

                log.info("Successfully generated {} more icons for service: {} in {}ms",
                        newIcons.size(), request.getServiceName(), response.getGenerationTimeMs());

                return response;

            } catch (Exception e) {
                log.error("Error generating more icons for service: {}", request.getServiceName(), e);
                return createErrorResponse(request, "Failed to generate more icons: " + e.getMessage(), startTime);
            }
        }).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Async error in generate more icons", throwable);
                deferredResult.setErrorResult(createErrorResponse(request, "Internal server error: " + throwable.getMessage(), System.currentTimeMillis()));
            } else {
                deferredResult.setResult(result);
            }
        });

        deferredResult.onTimeout(() -> {
            log.warn("Generate more icons request timed out for service: {}", request.getServiceName());
            deferredResult.setResult(createErrorResponse(request, "Request timed out - generation may still be in progress", System.currentTimeMillis()));
        });

        return deferredResult;
    }

    private CompletableFuture<byte[]> getServiceAndGenerate(String serviceName, String prompt, byte[] originalImageData, Long seed) {
        log.info("Generating more icons with service: {} using seed: {}", serviceName, seed);

        switch (serviceName.toLowerCase()) {
            case "flux":
                if (!aiServicesConfig.isFluxAiEnabled()) {
                    throw new RuntimeException("Flux service is disabled");
                }
                return fluxModelService.generateImageToImage(prompt, originalImageData, seed);

            case "recraft":
                if (!aiServicesConfig.isRecraftEnabled()) {
                    throw new RuntimeException("Recraft service is disabled");
                }
                return recraftModelService.generateImageToImage(prompt, originalImageData, seed);

            case "photon":
                if (!aiServicesConfig.isPhotonEnabled()) {
                    throw new RuntimeException("Photon service is disabled");
                }
                return photonModelService.generateImageToImage(prompt, originalImageData, seed);

            case "gpt":
                if (!aiServicesConfig.isGptEnabled()) {
                    throw new RuntimeException("GPT service is disabled");
                }
                return gptModelService.generateImageToImage(prompt, originalImageData, seed);

            case "imagen":
                if (!aiServicesConfig.isImagenEnabled()) {
                    throw new RuntimeException("Imagen service is disabled");
                }
                return imagenModelService.generateImageToImage(prompt, originalImageData, seed);

            case "banana":
                if (!aiServicesConfig.isBananaEnabled()) {
                    throw new RuntimeException("Banana service is disabled");
                }
                return bananaModelService.generateImageToImage(prompt, originalImageData, seed);

            default:
                throw new RuntimeException("Unknown service: " + serviceName);
        }
    }

    private List<IconGenerationResponse.GeneratedIcon> createIconList(List<String> base64Icons, MoreIconsRequest request) {
        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();

        for (int i = 0; i < base64Icons.size(); i++) {
            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
            icon.setId(UUID.randomUUID().toString());
            icon.setBase64Data(base64Icons.get(i));

            if (request.getIconDescriptions() != null && i < request.getIconDescriptions().size()) {
                icon.setDescription(request.getIconDescriptions().get(i));
            } else {
                icon.setDescription("Generated Icon " + (i + 1));
            }

            icon.setGridPosition(i);
            icon.setServiceSource(request.getServiceName());
            icons.add(icon);
        }

        return icons;
    }

    private MoreIconsResponse createErrorResponse(MoreIconsRequest request, String errorMessage, long startTime) {
        MoreIconsResponse response = new MoreIconsResponse();
        response.setStatus("error");
        response.setMessage(errorMessage);
        response.setServiceName(request.getServiceName());
        response.setNewIcons(new ArrayList<>());
        response.setOriginalRequestId(request.getOriginalRequestId());
        response.setGenerationTimeMs(System.currentTimeMillis() - startTime);
        return response;
    }

    private void persistMoreIcons(MoreIconsRequest request, List<IconGenerationResponse.GeneratedIcon> newIcons) {
        try {
            var defaultUser = dataInitializationService.getDefaultUser();
            String iconType = (request.getGenerationIndex() == 1) ? "original" : "variation";

            for (IconGenerationResponse.GeneratedIcon icon : newIcons) {
                if (icon.getBase64Data() != null && !icon.getBase64Data().isEmpty()) {
                    String fileName = String.format("%s_%s_%d.png",
                            icon.getServiceSource(),
                            icon.getId().substring(0, 8),
                            icon.getGridPosition());

                    String filePath = fileStorageService.saveIcon(
                            defaultUser.getDirectoryPath(),
                            request.getOriginalRequestId(),
                            iconType,
                            fileName,
                            icon.getBase64Data()
                    );

                    GeneratedIcon generatedIcon = new GeneratedIcon();
                    generatedIcon.setRequestId(request.getOriginalRequestId());
                    generatedIcon.setIconId(icon.getId());
                    generatedIcon.setUser(defaultUser);
                    generatedIcon.setFileName(fileName);
                    generatedIcon.setFilePath(filePath);
                    generatedIcon.setServiceSource(icon.getServiceSource());
                    generatedIcon.setGridPosition(icon.getGridPosition());
                    generatedIcon.setDescription(icon.getDescription());
                    generatedIcon.setTheme(request.getGeneralDescription());
                    generatedIcon.setIconCount(9);
                    generatedIcon.setGenerationIndex(request.getGenerationIndex());
                    generatedIcon.setIconType(iconType);

                    long fileSize = fileStorageService.getFileSize(defaultUser.getDirectoryPath(), request.getOriginalRequestId(), iconType, fileName);
                    generatedIcon.setFileSize(fileSize);

                    generatedIconRepository.save(generatedIcon);
                }
            }

        } catch (Exception e) {
            log.error("Error persisting more icons for request {}", request.getOriginalRequestId(), e);
            throw e;
        }
    }
}
