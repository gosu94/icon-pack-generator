package com.gosu.iconpackgenerator.domain.illustrations.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.illustrations.controller.api.IllustrationGenerationControllerAPI;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
import com.gosu.iconpackgenerator.domain.illustrations.dto.MoreIllustrationsRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.MoreIllustrationsResponse;
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationGenerationServiceV2;
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationPersistenceService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IllustrationGenerationController implements IllustrationGenerationControllerAPI {
    
    private final IllustrationGenerationServiceV2 illustrationGenerationService;
    private final ObjectMapper objectMapper;
    private final StreamingStateStore streamingStateStore;
    private final IllustrationPersistenceService illustrationPersistenceService;
    private final CoinManagementService coinManagementService;


    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(5);
    
    @PreDestroy
    public void cleanup() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            log.info("Shutting down Illustration SSE heartbeat scheduler");
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Override
    @ResponseBody
    public CompletableFuture<IllustrationGenerationResponse> generateIllustrations(
            @Valid @RequestBody IllustrationGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!request.isValid()) {
            throw new IllegalArgumentException("Either general description or reference image must be provided");
        }
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }
        
        User user = customUser.getUser();
        log.info("Illustration generation request from user: {}", user.getEmail());
        
        if (request.hasReferenceImage()) {
            log.info("Received reference image-based illustration generation request for {} illustrations", 
                request.getIllustrationCount());
        } else {
            log.info("Received text-based illustration generation request for {} illustrations with theme: {}",
                request.getIllustrationCount(), request.getGeneralDescription());
        }
        
        if (request.getIndividualDescriptions() == null) {
            request.setIndividualDescriptions(new ArrayList<>());
        }
        
        while (request.getIndividualDescriptions().size() < request.getIllustrationCount()) {
            request.getIndividualDescriptions().add("");
        }
        
        return illustrationGenerationService.generateIllustrations(request, user)
            .whenComplete((response, error) -> {
                if (error != null) {
                    log.error("Error generating illustrations", error);
                } else {
                    log.info("Successfully generated illustrations for request: {}", response.getRequestId());
                }
            });
    }
    
    @Override
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startStreamingGeneration(
            @Valid @RequestBody IllustrationGenerationRequest request, 
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!request.isValid()) {
            throw new IllegalArgumentException("Either general description or reference image must be provided");
        }
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }
        
        User user = customUser.getUser();
        log.info("Streaming illustration generation request from user: {}", user.getEmail());
        
        String requestId = UUID.randomUUID().toString();
        streamingStateStore.addRequest(requestId, request); // Store the actual request
        
        CompletableFuture.runAsync(() -> {
            processStreamingGeneration(requestId, request, user);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        
        // Frontend expects enabledServices for initialization
        // Illustrations only use Banana service
        Map<String, Boolean> enabledServices = new HashMap<>();
        enabledServices.put("banana", true);
        // Set other services to false for illustrations
        enabledServices.put("flux", false);
        enabledServices.put("recraft", false);
        enabledServices.put("photon", false);
        enabledServices.put("gpt", false);
        response.put("enabledServices", enabledServices);
        
        return ResponseEntity.ok(response);
    }
    
    @Override
    @ResponseBody
    public SseEmitter connectToStream(@PathVariable String requestId) {
        log.info("Client connecting to illustration stream for request: {}", requestId);
        
        SseEmitter emitter = new SseEmitter(600_000L); // 10 minutes
        streamingStateStore.addEmitter(requestId, emitter);
        
        emitter.onCompletion(() -> {
            log.info("Illustration SSE completed for request: {}", requestId);
            streamingStateStore.removeEmitter(requestId);
            // Keep request in state store briefly for potential recovery (cleaned up by whenComplete)
        });
        emitter.onTimeout(() -> {
            log.warn("Illustration SSE timeout for request: {}", requestId);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            // Check if this is a client disconnection (expected on mobile when switching apps)
            boolean isClientDisconnect = throwable instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException
                    || (throwable.getCause() != null && throwable.getCause() instanceof java.io.IOException 
                        && (throwable.getCause().getMessage() != null 
                            && (throwable.getCause().getMessage().contains("Broken pipe")
                                || throwable.getCause().getMessage().contains("Connection reset")
                                || throwable.getCause().getMessage().contains("Socket closed"))));
            
            if (isClientDisconnect) {
                // Client disconnected (e.g., switched apps on mobile) - this is expected
                log.debug("Client disconnected from illustration SSE for request: {} - generation continues in background for recovery", requestId);
                streamingStateStore.removeEmitter(requestId);
                // Keep request in state store for recovery - it will be cleaned up when generation completes
                // Don't call completeWithError as the connection is already gone
            } else {
                // Unexpected error - log and clean up
                log.error("Illustration SSE error for request: {}", requestId, throwable);
                streamingStateStore.removeEmitter(requestId);
                streamingStateStore.removeRequest(requestId);
                try {
                    emitter.completeWithError(throwable);
                } catch (Exception e) {
                    log.debug("Could not complete emitter with error: {}", e.getMessage());
                }
            }
        });
        
        return emitter;
    }
    
    private void processStreamingGeneration(String requestId, IllustrationGenerationRequest request, User user) {
        ScheduledFuture<?> heartbeatTask = null;
        
        try {
            if (!request.isValid()) {
                throw new IllegalArgumentException("Either general description or reference image must be provided");
            }
            
            if (request.getIndividualDescriptions() == null) {
                request.setIndividualDescriptions(new ArrayList<>());
            }
            
            while (request.getIndividualDescriptions().size() < request.getIllustrationCount()) {
                request.getIndividualDescriptions().add("");
            }
            
            // Start heartbeat with error handling to prevent exceptions after completion
            log.debug("Starting heartbeat for illustration request: {}", requestId);
            heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        if (!isConnectionActive(requestId)) {
                            log.debug("Connection lost during illustration generation for request: {}", requestId);
                        }
                    } catch (IllegalStateException e) {
                        // Emitter already completed - this is expected, ignore
                        log.debug("Heartbeat skipped for request {} - emitter already completed", requestId);
                    } catch (Exception e) {
                        log.debug("Heartbeat error for request {}: {}", requestId, e.getMessage());
                    }
                },
                5, 5, TimeUnit.SECONDS
            );
            
            final ScheduledFuture<?> finalHeartbeatTask = heartbeatTask;
            
            illustrationGenerationService.generateIllustrations(request, requestId, update -> {
                SseEmitter emitter = streamingStateStore.getEmitter(requestId);
                if (emitter != null) {
                    try {
                        String jsonUpdate = objectMapper.writeValueAsString(update);
                        boolean sent = safeSendSseUpdate(emitter, requestId, update.getEventType(), jsonUpdate);
                        
                        if ("generation_complete".equals(update.getEventType())) {
                            // Cancel heartbeat before completing emitter to prevent race conditions
                            if (finalHeartbeatTask != null && !finalHeartbeatTask.isCancelled()) {
                                finalHeartbeatTask.cancel(false);
                                log.debug("Cancelled heartbeat for completed generation: {}", requestId);
                            }
                            
                            // Complete the emitter if we successfully sent the final update
                            if (sent) {
                                try {
                                    emitter.complete();
                                    log.debug("Successfully completed illustration SSE stream for request: {}", requestId);
                                } catch (IllegalStateException e) {
                                    log.debug("Emitter already completed for request: {}", requestId);
                                } catch (Exception e) {
                                    log.debug("Error completing emitter for request: {} - {}", requestId, e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error preparing illustration SSE update for request: {}", requestId, e);
                        if (finalHeartbeatTask != null && !finalHeartbeatTask.isCancelled()) {
                            finalHeartbeatTask.cancel(false);
                        }
                    }
                }
            }, user).whenComplete((response, error) -> {
                if (finalHeartbeatTask != null && !finalHeartbeatTask.isCancelled()) {
                    finalHeartbeatTask.cancel(false);
                }
                
                SseEmitter emitter = streamingStateStore.getEmitter(requestId);
                
                if (error != null) {
                    // Generation failed with error
                    if (emitter != null) {
                        log.error("Error in streaming illustration generation for request: {}", requestId, error);
                        try {
                            // Sanitize error message for user display
                            String sanitizedError = sanitizeErrorMessage(
                                error instanceof Exception ? (Exception) error : new Exception(error));
                            
                            ServiceProgressUpdate errorUpdate = new ServiceProgressUpdate();
                            errorUpdate.setRequestId(requestId);
                            errorUpdate.setEventType("generation_error");
                            errorUpdate.setStatus("error");
                            errorUpdate.setMessage(sanitizedError);
                            
                            String jsonUpdate = objectMapper.writeValueAsString(errorUpdate);
                            boolean sent = safeSendSseUpdate(emitter, requestId, "generation_error", jsonUpdate);
                            
                            if (sent) {
                                try {
                                    emitter.completeWithError(error);
                                } catch (Exception e) {
                                    log.debug("Error completing emitter with error: {}", e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error preparing error update for request: {}", requestId, e);
                        }
                    } else {
                        log.error("Error in streaming illustration generation for request: {} (client disconnected)", requestId, error);
                    }
                    // Clean up on error
                    streamingStateStore.removeRequest(requestId);
                } else if (response != null) {
                    // Generation succeeded - store response for retrieval
                    streamingStateStore.addResponse(requestId, response);
                    if (emitter == null) {
                        log.info("Stored illustration response for request: {} (client disconnected during generation)", requestId);
                    }
                    // Clean up the request now that generation is complete
                    streamingStateStore.removeRequest(requestId);
                }
            });
            
        } catch (Exception e) {
            log.error("Error in processStreamingGeneration for illustrations: {}", requestId, e);
            
            if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
                heartbeatTask.cancel(false);
            }
            
            SseEmitter emitter = streamingStateStore.getEmitter(requestId);
            if (emitter != null) {
                emitter.completeWithError(e);
            }
        }
    }
    
    @Override
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkGenerationStatus(
            @PathVariable String requestId, 
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "User not authenticated");
            return ResponseEntity.status(401).body(errorResponse);
        }
        
        User user = customUser.getUser();
        log.info("Checking illustration generation status for request: {} by user: {}", requestId, user.getEmail());
        
        Map<String, Object> response = new HashMap<>();
        
        // Check if generation has completed
        // Note: We're reusing the same StreamingStateStore as icons for simplicity
        Object storedResponse = null;
        try {
            storedResponse = streamingStateStore.getResponse(requestId);
        } catch (ClassCastException e) {
            // Response exists but is of different type (icon vs illustration)
            log.debug("Response {} exists but is of different type (icon)", requestId);
        }
        
        if (storedResponse != null) {
            response.put("status", "completed");
            response.put("message", "Generation completed");
            response.put("data", storedResponse);
            log.info("Found completed illustration generation for request: {}", requestId);
            return ResponseEntity.ok(response);
        }
        
        // Check if request is still in progress
        Object activeRequest = null;
        try {
            activeRequest = streamingStateStore.getRequest(requestId);
        } catch (ClassCastException e) {
            // Request exists but is of different type (icon vs illustration)
            log.debug("Request {} exists but is of different type (icon)", requestId);
        }
        
        if (activeRequest != null || streamingStateStore.getEmitter(requestId) != null) {
            response.put("status", "in_progress");
            response.put("message", "Generation is still in progress");
            log.info("Illustration generation still in progress for request: {}", requestId);
            return ResponseEntity.ok(response);
        }
        
        // Request not found
        response.put("status", "not_found");
        response.put("message", "Generation request not found or expired");
        log.info("Illustration generation request not found: {}", requestId);
        return ResponseEntity.status(404).body(response);
    }
    
    private boolean safeSendSseUpdate(SseEmitter emitter, String requestId, String eventName, String data) {
        if (emitter == null) {
            return false;
        }
        
        try {
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data));
            return true;
        } catch (IllegalStateException e) {
            // Emitter already completed - this is expected in race conditions with heartbeat
            log.debug("Cannot send SSE update for request {} - emitter already completed", requestId);
            streamingStateStore.removeEmitter(requestId);
            return false;
        } catch (org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
            log.debug("Client disconnected from illustration SSE stream for request: {} - {}", requestId, e.getMessage());
            streamingStateStore.removeEmitter(requestId);
            return false;
        } catch (java.io.IOException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Broken pipe") ||
                    e.getMessage().contains("Connection reset") ||
                    e.getMessage().contains("Socket closed"))) {
                log.debug("Client connection lost for illustration SSE stream for request: {} - {}", requestId, e.getMessage());
                streamingStateStore.removeEmitter(requestId);
                return false;
            } else {
                log.error("I/O error sending illustration SSE update for request: {}", requestId, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception completionError) {
                    log.debug("Error completing emitter after I/O error: {}", completionError.getMessage());
                }
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending illustration SSE update for request: {}", requestId, e);
            try {
                emitter.completeWithError(e);
            } catch (Exception completionError) {
                log.debug("Error completing emitter after general error: {}", completionError.getMessage());
            }
            return false;
        }
    }
    
    private boolean sendHeartbeat(String requestId) {
        SseEmitter emitter = streamingStateStore.getEmitter(requestId);
        if (emitter == null) {
            return false;
        }
        
        return safeSendSseUpdate(emitter, requestId, "heartbeat",
            "{\"timestamp\": " + System.currentTimeMillis() + ", \"status\": \"processing\"}");
    }
    
    private boolean isConnectionActive(String requestId) {
        SseEmitter emitter = streamingStateStore.getEmitter(requestId);
        if (emitter == null) {
            log.debug("No emitter found for illustration request: {}", requestId);
            return false;
        }
        
        return sendHeartbeat(requestId);
    }
    
    @Override
    @ResponseBody
    public DeferredResult<MoreIllustrationsResponse> generateMoreIllustrations(
            @RequestBody MoreIllustrationsRequest request, @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            DeferredResult<MoreIllustrationsResponse> result = new DeferredResult<>();
            result.setResult(createErrorResponse(request, "User not authenticated", System.currentTimeMillis()));
            return result;
        }

        User user = customUser.getUser();
        log.info("Received generate more illustrations request from user {} with {} descriptions for generation index: {}",
                user.getEmail(),
                request.getIllustrationDescriptions() != null ? request.getIllustrationDescriptions().size() : 0,
                request.getGenerationIndex());

        DeferredResult<MoreIllustrationsResponse> deferredResult = new DeferredResult<>(300000L);

        CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            // Deduct coins using the dedicated service
            CoinManagementService.CoinDeductionResult coinResult = coinManagementService.deductCoinForMoreIcons(user);
            if (!coinResult.isSuccess()) {
                return createErrorResponse(request, coinResult.getErrorMessage(), startTime);
            }

            final boolean usedTrialCoin = coinResult.isUsedTrialCoins();

            try {
                if (request.getOriginalImageBase64() == null || request.getOriginalImageBase64().trim().isEmpty()) {
                    return createErrorResponse(request, "Original image is required", startTime);
                }

                byte[] originalImageData = Base64.getDecoder().decode(request.getOriginalImageBase64());
                
                // Use a varied seed to ensure different results each time
                // Add a random component to avoid generating identical illustrations
                Long variedSeed = request.getSeed() != null ? 
                        request.getSeed() + System.currentTimeMillis() % 10000 :
                        System.currentTimeMillis() % 10000;
                
                // V2: Pass raw general description - the service will generate individual prompts for each illustration
                // This ensures each illustration gets its own unique prompt based on its specific description
                CompletableFuture<List<IllustrationGenerationResponse.GeneratedIllustration>> generationFuture = 
                        illustrationGenerationService.generateMoreIllustrationsFromImage(
                                originalImageData, 
                                request.getGeneralDescription(), 
                                variedSeed, 
                                request.getIllustrationDescriptions());
                
                List<IllustrationGenerationResponse.GeneratedIllustration> newIllustrations = generationFuture.join();

                // Persist the new illustrations
                try {
                    illustrationPersistenceService.persistMoreIllustrations(
                            request.getOriginalRequestId(), 
                            newIllustrations, 
                            user, 
                            request.getGeneralDescription(), 
                            request.getGenerationIndex());
                    log.info("Successfully persisted {} more illustrations for request {}", 
                            newIllustrations.size(), request.getOriginalRequestId());
                } catch (Exception e) {
                    log.error("Error persisting more illustrations for request {}", 
                            request.getOriginalRequestId(), e);
                }

                // Update the stored response with new illustrations for export functionality
                try {
                    updateStoredResponseWithMoreIllustrations(request, newIllustrations);
                    log.info("Successfully updated stored response with {} more illustrations for request {}", 
                            newIllustrations.size(), request.getOriginalRequestId());
                } catch (Exception e) {
                    log.error("Error updating stored response with more illustrations for request {}", 
                            request.getOriginalRequestId(), e);
                }

                MoreIllustrationsResponse response = new MoreIllustrationsResponse();
                response.setStatus("success");
                response.setMessage("More illustrations generated successfully with same style");
                response.setServiceName("banana");
                response.setNewIllustrations(newIllustrations);
                response.setOriginalRequestId(request.getOriginalRequestId());
                response.setGenerationTimeMs(System.currentTimeMillis() - startTime);

                log.info("Successfully generated {} more illustrations in {}ms",
                        newIllustrations.size(), response.getGenerationTimeMs());

                return response;

            } catch (Exception e) {
                log.error("Error generating more illustrations", e);

                // Refund coins on error
                try {
                    coinManagementService.refundCoins(user, 1, usedTrialCoin);
                    log.info("Refunded {} coin to user {} due to illustration generation error",
                            usedTrialCoin ? "trial" : "regular", user.getEmail());
                } catch (Exception refundException) {
                    log.error("Failed to refund coins to user {} for more illustrations generation", 
                            user.getEmail(), refundException);
                }

                // Sanitize error message for user display
                String sanitizedError = sanitizeErrorMessage(e);
                return createErrorResponse(request, sanitizedError, startTime);
            }
        }).whenComplete((response, throwable) -> {
            if (throwable != null) {
                log.error("Error in more illustrations generation future", throwable);
                
                // Sanitize error message
                String sanitizedError = sanitizeErrorMessage(
                    throwable instanceof Exception ? (Exception) throwable : new Exception(throwable));
                    
                deferredResult.setResult(createErrorResponse(request, 
                        sanitizedError, System.currentTimeMillis()));
            } else {
                deferredResult.setResult(response);
            }
        });

        return deferredResult;
    }
    
    private MoreIllustrationsResponse createErrorResponse(
            MoreIllustrationsRequest request, String message, long startTime) {
        MoreIllustrationsResponse response = new MoreIllustrationsResponse();
        response.setStatus("error");
        response.setMessage(message);
        response.setServiceName("banana");
        response.setOriginalRequestId(request.getOriginalRequestId());
        response.setGenerationTimeMs(System.currentTimeMillis() - startTime);
        response.setNewIllustrations(new ArrayList<>());
        return response;
    }
    
    private void updateStoredResponseWithMoreIllustrations(
            MoreIllustrationsRequest request, 
            List<IllustrationGenerationResponse.GeneratedIllustration> newIllustrations) {
        try {
            IllustrationGenerationResponse storedResponse = streamingStateStore.getResponse(request.getOriginalRequestId());
            if (storedResponse == null) {
                log.warn("No stored response found for request: {}", request.getOriginalRequestId());
                return;
            }

            // Illustrations only use Banana service
            List<IllustrationGenerationResponse.ServiceResults> serviceResults = storedResponse.getBananaResults();
            
            if (serviceResults == null) {
                log.warn("No banana results found in request {}", request.getOriginalRequestId());
                return;
            }

            // Find the existing ServiceResults entry for this generation index
            IllustrationGenerationResponse.ServiceResults targetResult = null;
            for (IllustrationGenerationResponse.ServiceResults result : serviceResults) {
                if (result.getGenerationIndex() == request.getGenerationIndex()) {
                    targetResult = result;
                    break;
                }
            }

            if (targetResult != null) {
                // Add new illustrations to existing generation index
                if (targetResult.getIllustrations() == null) {
                    targetResult.setIllustrations(new ArrayList<>());
                }
                targetResult.getIllustrations().addAll(newIllustrations);
                log.info("Added {} new illustrations to existing generation {} for service {}",
                        newIllustrations.size(), request.getGenerationIndex(), request.getServiceName());
            } else {
                // Create new ServiceResults for this generation index (shouldn't normally happen)
                IllustrationGenerationResponse.ServiceResults newResult = new IllustrationGenerationResponse.ServiceResults();
                newResult.setServiceName(request.getServiceName());
                newResult.setStatus("success");
                newResult.setMessage("More illustrations generated successfully");
                newResult.setIllustrations(new ArrayList<>(newIllustrations));
                newResult.setGenerationIndex(request.getGenerationIndex());
                serviceResults.add(newResult);
                log.info("Created new generation {} entry with {} illustrations for service {}",
                        request.getGenerationIndex(), newIllustrations.size(), request.getServiceName());
            }

            // Update the stored response
            streamingStateStore.addResponse(request.getOriginalRequestId(), storedResponse);
            log.info("Successfully updated stored response for request {} with {} more illustrations",
                    request.getOriginalRequestId(), newIllustrations.size());

        } catch (Exception e) {
            log.error("Error updating stored response with more illustrations for request {}", request.getOriginalRequestId(), e);
            // Don't rethrow - updating stored response is not critical for the core functionality
            // The illustrations have already been generated and persisted successfully
        }
    }
    
    /**
     * Sanitize error messages for user display, especially for content policy violations
     */
    private String sanitizeErrorMessage(Exception e) {
        String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
        
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
        return "Failed to generate illustrations. Please try again or contact support if the issue persists.";
    }
}

