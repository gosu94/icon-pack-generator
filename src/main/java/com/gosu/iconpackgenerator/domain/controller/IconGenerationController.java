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
import com.gosu.iconpackgenerator.domain.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.service.FluxModelService;
import com.gosu.iconpackgenerator.domain.service.GptModelService;
import com.gosu.iconpackgenerator.domain.service.IconGenerationService;
import com.gosu.iconpackgenerator.domain.service.IconPersistenceService;
import com.gosu.iconpackgenerator.domain.service.ImageProcessingService;
import com.gosu.iconpackgenerator.domain.service.ImagenModelService;
import com.gosu.iconpackgenerator.domain.service.PhotonModelService;
import com.gosu.iconpackgenerator.domain.service.PromptGenerationService;
import com.gosu.iconpackgenerator.domain.service.RecraftModelService;
import com.gosu.iconpackgenerator.domain.service.ServiceFailureHandler;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
    private final PromptGenerationService promptGenerationService;
    private final ImageProcessingService imageProcessingService;
    private final AIServicesConfig aiServicesConfig;
    private final ObjectMapper objectMapper;
    private final StreamingStateStore streamingStateStore;
    private final CoinManagementService coinManagementService;
    private final ServiceFailureHandler serviceFailureHandler;
    private final IconPersistenceService iconPersistenceService;

    @Override
    @ResponseBody
    public CompletableFuture<IconGenerationResponse> generateIcons(@Valid @RequestBody IconGenerationRequest request,
                                                                   @AuthenticationPrincipal OAuth2User principal) {
        if (!request.isValid()) {
            throw new IllegalArgumentException("Either general description or reference image must be provided");
        }

        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }

        User user = customUser.getUser();
        log.info("Icon generation request from user: {}", user.getEmail());

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

        return iconGenerationService.generateIcons(request, user)
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
    public ResponseEntity<Map<String, Object>> startStreamingGeneration(@Valid @RequestBody IconGenerationRequest request, @AuthenticationPrincipal OAuth2User principal) {
        if (!request.isValid()) {
            throw new IllegalArgumentException("Either general description or reference image must be provided");
        }

        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }

        User user = customUser.getUser();
        log.info("Streaming icon generation request from user: {}", user.getEmail());

        if (request.hasReferenceImage()) {
            log.info("Starting streaming reference image-based icon generation for {} icons", request.getIconCount());
        } else {
            log.info("Starting streaming text-based icon generation for {} icons with theme: {}",
                    request.getIconCount(), request.getGeneralDescription());
        }

        String requestId = UUID.randomUUID().toString();
        streamingStateStore.addRequest(requestId, request);

        CompletableFuture.runAsync(() -> {
            processStreamingGeneration(requestId, request, user);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        Map<String, Boolean> enabledServices = new HashMap<>();
        enabledServices.put("flux", aiServicesConfig.isFluxAiEnabled());
        enabledServices.put("recraft", aiServicesConfig.isRecraftEnabled());
        enabledServices.put("photon", aiServicesConfig.isPhotonEnabled());
        enabledServices.put("gpt", aiServicesConfig.isGptEnabled());
        enabledServices.put("imagen", aiServicesConfig.isImagenEnabled());
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

    private void processStreamingGeneration(String requestId, IconGenerationRequest request, User user) {
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
                        boolean sent = safeSendSseUpdate(emitter, requestId, update.getEventType(), jsonUpdate);
                        
                        if (sent && "generation_complete".equals(update.getEventType())) {
                            try {
                                emitter.complete();
                            } catch (Exception e) {
                                log.debug("Error completing emitter for request: {} - {}", requestId, e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error preparing SSE update for request: {}", requestId, e);
                    }
                }
            }, user).whenComplete((response, error) -> {
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
                        boolean sent = safeSendSseUpdate(emitter, requestId, "generation_error", jsonUpdate);
                        
                        if (sent) {
                            try {
                                emitter.completeWithError(error);
                            } catch (Exception e) {
                                log.debug("Error completing emitter with error for request: {} - {}", requestId, e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error preparing error update for request: {}", requestId, e);
                    }
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
    public DeferredResult<MoreIconsResponse> generateMoreIcons(@RequestBody MoreIconsRequest request, @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            DeferredResult<MoreIconsResponse> result = new DeferredResult<>();
            result.setResult(createErrorResponse(request, "User not authenticated", System.currentTimeMillis()));
            return result;
        }

        User user = customUser.getUser();
        log.info("Received generate more icons request from user {} for service: {} with {} icon descriptions for generation index: {}",
                user.getEmail(),
                request.getServiceName(),
                request.getIconDescriptions() != null ? request.getIconDescriptions().size() : 0,
                request.getGenerationIndex());

        DeferredResult<MoreIconsResponse> deferredResult = new DeferredResult<>(300000L);

        CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            // Deduct coins using the dedicated service
            CoinManagementService.CoinDeductionResult coinResult = coinManagementService.deductCoinForMoreIcons(user);
            if (!coinResult.isSuccess()) {
                return createErrorResponse(request, coinResult.getErrorMessage(), startTime);
            }
            
            final boolean usedTrialCoin = coinResult.isUsedTrialCoins();

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
                List<String> base64Icons = imageProcessingService.cropIconsFromGrid(newImageData, 9, true, 0, true, true);
                List<IconGenerationResponse.GeneratedIcon> newIcons = createIconList(base64Icons, request);

                try {
                    iconPersistenceService.persistMoreIcons(request.getOriginalRequestId(), newIcons, user, 
                                                           request.getServiceName(), request.getGeneralDescription(), 
                                                           request.getGenerationIndex());
                    log.info("Successfully persisted {} more icons for request {}", newIcons.size(), request.getOriginalRequestId());
                } catch (Exception e) {
                    log.error("Error persisting more icons for request {}", request.getOriginalRequestId(), e);
                }
                
                // Update the stored response with new icons for export functionality
                try {
                    updateStoredResponseWithMoreIcons(request, newIcons);
                    log.info("Successfully updated stored response with {} more icons for request {}", newIcons.size(), request.getOriginalRequestId());
                } catch (Exception e) {
                    log.error("Error updating stored response with more icons for request {}", request.getOriginalRequestId(), e);
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
                
                // Check if this is a temporary service failure and refund coins if needed
                String errorMessage = e.getMessage();
                ServiceFailureHandler.FailureAnalysisResult failureAnalysis = 
                    serviceFailureHandler.analyzeSingleServiceFailure(errorMessage, request.getServiceName());
                
                if (failureAnalysis.shouldRefund()) {
                    try {
                        coinManagementService.refundCoins(user, 1, usedTrialCoin);
                        log.info("Refunded {} coin to user {} due to temporary service unavailability for more icons generation", 
                                usedTrialCoin ? "trial" : "regular", user.getEmail());
                        return createErrorResponse(request, failureAnalysis.getRefundMessage(), startTime);
                    } catch (Exception refundException) {
                        log.error("Failed to refund coins to user {} for more icons generation", user.getEmail(), refundException);
                        // Fall through to original error response if refund fails
                    }
                }
                
                return createErrorResponse(request, "Failed to generate more icons: " + errorMessage, startTime);
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
                return gptModelService.generateImageToImage(prompt, originalImageData, 0L);

            case "imagen":
                if (!aiServicesConfig.isImagenEnabled()) {
                    throw new RuntimeException("Imagen service is disabled");
                }
                return imagenModelService.generateImageToImage(prompt, originalImageData, seed);

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

    /**
     * Safely send SSE update with graceful handling of client disconnections
     */
    private boolean safeSendSseUpdate(SseEmitter emitter, String requestId, String eventName, String data) {
        if (emitter == null) {
            return false;
        }
        
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            return true;
        } catch (org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
            // Client disconnected - this is expected behavior, not an error
            log.debug("Client disconnected from SSE stream for request: {} - {}", requestId, e.getMessage());
            streamingStateStore.removeEmitter(requestId);
            return false;
        } catch (java.io.IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                // Client disconnected - this is expected behavior, not an error
                log.debug("Client connection broken for SSE stream for request: {} - {}", requestId, e.getMessage());
                streamingStateStore.removeEmitter(requestId);
                return false;
            } else {
                log.error("I/O error sending SSE update for request: {}", requestId, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception completionError) {
                    log.debug("Error completing emitter after I/O error: {}", completionError.getMessage());
                }
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending SSE update for request: {}", requestId, e);
            try {
                emitter.completeWithError(e);
            } catch (Exception completionError) {
                log.debug("Error completing emitter after general error: {}", completionError.getMessage());
            }
            return false;
        }
    }

    
    /**
     * Updates the stored response with new icons from "Generate More Icons" request
     */
    private void updateStoredResponseWithMoreIcons(MoreIconsRequest request, List<IconGenerationResponse.GeneratedIcon> newIcons) {
        try {
            // Get the existing stored response
            IconGenerationResponse existingResponse = streamingStateStore.getResponse(request.getOriginalRequestId());
            if (existingResponse == null) {
                log.warn("No existing response found for request {}, cannot update with more icons", request.getOriginalRequestId());
                return;
            }
            
            // Get the appropriate service results list based on service name
            List<IconGenerationResponse.ServiceResults> serviceResults = switch (request.getServiceName().toLowerCase()) {
                case "flux" -> existingResponse.getFalAiResults();
                case "recraft" -> existingResponse.getRecraftResults();
                case "photon" -> existingResponse.getPhotonResults();
                case "gpt" -> existingResponse.getGptResults();
                case "imagen" -> existingResponse.getImagenResults();
                default -> null;
            };
            
            if (serviceResults == null) {
                log.warn("No service results found for service {} in request {}", request.getServiceName(), request.getOriginalRequestId());
                return;
            }
            
            // Find or create a ServiceResults entry for this generation index
            IconGenerationResponse.ServiceResults targetResult = null;
            for (IconGenerationResponse.ServiceResults result : serviceResults) {
                if (result.getGenerationIndex() == request.getGenerationIndex()) {
                    targetResult = result;
                    break;
                }
            }
            
            if (targetResult != null) {
                // Add new icons to existing generation index
                if (targetResult.getIcons() == null) {
                    targetResult.setIcons(new ArrayList<>());
                }
                targetResult.getIcons().addAll(newIcons);
                log.info("Added {} new icons to existing generation {} for service {}", 
                        newIcons.size(), request.getGenerationIndex(), request.getServiceName());
            } else {
                // Create new ServiceResults for this generation index (this shouldn't normally happen)
                IconGenerationResponse.ServiceResults newResult = new IconGenerationResponse.ServiceResults();
                newResult.setStatus("success");
                newResult.setMessage("More icons generated successfully");
                newResult.setIcons(new ArrayList<>(newIcons));
                newResult.setGenerationIndex(request.getGenerationIndex());
                serviceResults.add(newResult);
                log.info("Created new generation {} entry with {} icons for service {}", 
                        request.getGenerationIndex(), newIcons.size(), request.getServiceName());
            }
            
            // Update the stored response
            streamingStateStore.addResponse(request.getOriginalRequestId(), existingResponse);
            log.info("Successfully updated stored response for request {} with {} more icons", 
                    request.getOriginalRequestId(), newIcons.size());
            
        } catch (Exception e) {
            log.error("Error updating stored response with more icons for request {}", request.getOriginalRequestId(), e);
            // Don't rethrow - updating stored response is not critical for the core functionality
            // The icons have already been generated and persisted successfully
        }
    }
    
    
}
