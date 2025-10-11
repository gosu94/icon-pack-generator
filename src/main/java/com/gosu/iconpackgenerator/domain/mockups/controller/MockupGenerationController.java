package com.gosu.iconpackgenerator.domain.mockups.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.mockups.controller.api.MockupGenerationControllerAPI;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse;
import com.gosu.iconpackgenerator.domain.mockups.dto.MoreMockupsRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MoreMockupsResponse;
import com.gosu.iconpackgenerator.domain.mockups.service.MockupGenerationService;
import com.gosu.iconpackgenerator.domain.mockups.service.MockupPersistenceService;
import com.gosu.iconpackgenerator.domain.mockups.service.MockupPromptGenerationService;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MockupGenerationController implements MockupGenerationControllerAPI {
    
    private final MockupGenerationService mockupGenerationService;
    private final ObjectMapper objectMapper;
    private final StreamingStateStore streamingStateStore;
    private final MockupPromptGenerationService mockupPromptGenerationService;
    private final MockupPersistenceService mockupPersistenceService;
    private final CoinManagementService coinManagementService;
    
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(5);
    
    @PreDestroy
    public void cleanup() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            log.info("Shutting down Mockup SSE heartbeat scheduler");
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
    public CompletableFuture<MockupGenerationResponse> generateMockups(
            @Valid @RequestBody MockupGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!request.isValid()) {
            throw new IllegalArgumentException("Either description or reference image must be provided");
        }
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }
        
        User user = customUser.getUser();
        log.info("Mockup generation request from user: {}", user.getEmail());
        
        if (request.hasReferenceImage()) {
            log.info("Received reference image-based mockup generation request");
        } else {
            log.info("Received text-based mockup generation request with description: {}",
                request.getDescription());
        }
        
        return mockupGenerationService.generateMockups(request, user)
            .whenComplete((response, error) -> {
                if (error != null) {
                    log.error("Error generating mockups", error);
                } else {
                    log.info("Successfully generated mockups for request: {}", response.getRequestId());
                }
            });
    }
    
    @Override
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startStreamingGeneration(
            @Valid @RequestBody MockupGenerationRequest request, 
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!request.isValid()) {
            throw new IllegalArgumentException("Either description or reference image must be provided");
        }
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }
        
        User user = customUser.getUser();
        log.info("Streaming mockup generation request from user: {}", user.getEmail());
        
        String requestId = UUID.randomUUID().toString();
        streamingStateStore.addRequest(requestId, request); // Store the actual request
        
        CompletableFuture.runAsync(() -> {
            processStreamingGeneration(requestId, request, user);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        
        // Frontend expects enabledServices for initialization
        // Mockups only use Banana service
        Map<String, Boolean> enabledServices = new HashMap<>();
        enabledServices.put("banana", true);
        // Set other services to false for mockups
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
        log.info("Client connecting to mockup stream for request: {}", requestId);
        
        SseEmitter emitter = new SseEmitter(600_000L); // 10 minutes
        streamingStateStore.addEmitter(requestId, emitter);
        
        emitter.onCompletion(() -> {
            log.info("Mockup SSE completed for request: {}", requestId);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
        });
        emitter.onTimeout(() -> {
            log.warn("Mockup SSE timeout for request: {}", requestId);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.error("Mockup SSE error for request: {}", requestId, throwable);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.completeWithError(throwable);
        });
        
        return emitter;
    }
    
    private void processStreamingGeneration(String requestId, MockupGenerationRequest request, User user) {
        ScheduledFuture<?> heartbeatTask = null;
        
        try {
            if (!request.isValid()) {
                throw new IllegalArgumentException("Either description or reference image must be provided");
            }
            
            // Start heartbeat
            log.debug("Starting heartbeat for mockup request: {}", requestId);
            heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(
                () -> {
                    if (!isConnectionActive(requestId)) {
                        log.debug("Connection lost during mockup generation for request: {}", requestId);
                    }
                },
                5, 5, TimeUnit.SECONDS
            );
            
            final ScheduledFuture<?> finalHeartbeatTask = heartbeatTask;
            
            mockupGenerationService.generateMockups(request, requestId, update -> {
                SseEmitter emitter = streamingStateStore.getEmitter(requestId);
                if (emitter != null) {
                    try {
                        String jsonUpdate = objectMapper.writeValueAsString(update);
                        boolean sent = safeSendSseUpdate(emitter, requestId, update.getEventType(), jsonUpdate);
                        
                        if ("generation_complete".equals(update.getEventType())) {
                            if (finalHeartbeatTask != null && !finalHeartbeatTask.isCancelled()) {
                                finalHeartbeatTask.cancel(false);
                            }
                            
                            if (sent && isConnectionActive(requestId)) {
                                try {
                                    emitter.complete();
                                    log.debug("Successfully completed mockup SSE stream for request: {}", requestId);
                                } catch (Exception e) {
                                    log.debug("Error completing emitter for request: {} - {}", requestId, e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error preparing mockup SSE update for request: {}", requestId, e);
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
                if (emitter != null && error != null) {
                    log.error("Error in streaming mockup generation for request: {}", requestId, error);
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
                } else if (response != null) {
                    // Store the response for later retrieval
                    streamingStateStore.addResponse(requestId, response);
                }
            });
            
        } catch (Exception e) {
            log.error("Error in processStreamingGeneration for mockups: {}", requestId, e);
            
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
        log.info("Checking mockup generation status for request: {} by user: {}", requestId, user.getEmail());
        
        Map<String, Object> response = new HashMap<>();
        
        // Check if generation has completed
        Object storedResponse = null;
        try {
            storedResponse = streamingStateStore.getResponse(requestId);
        } catch (ClassCastException e) {
            // Response exists but is of different type (icon vs mockup)
            log.debug("Response {} exists but is of different type (icon)", requestId);
        }
        
        if (storedResponse != null) {
            response.put("status", "completed");
            response.put("message", "Generation completed");
            response.put("data", storedResponse);
            log.info("Found completed mockup generation for request: {}", requestId);
            return ResponseEntity.ok(response);
        }
        
        // Check if request is still in progress
        Object activeRequest = null;
        try {
            activeRequest = streamingStateStore.getRequest(requestId);
        } catch (ClassCastException e) {
            // Request exists but is of different type (icon vs mockup)
            log.debug("Request {} exists but is of different type (icon)", requestId);
        }
        
        if (activeRequest != null || streamingStateStore.getEmitter(requestId) != null) {
            response.put("status", "in_progress");
            response.put("message", "Generation is still in progress");
            log.info("Mockup generation still in progress for request: {}", requestId);
            return ResponseEntity.ok(response);
        }
        
        // Request not found
        response.put("status", "not_found");
        response.put("message", "Generation request not found or expired");
        log.info("Mockup generation request not found: {}", requestId);
        return ResponseEntity.status(404).body(response);
    }
    
    @Override
    @ResponseBody
    public DeferredResult<MoreMockupsResponse> generateMoreMockups(
            @RequestBody MoreMockupsRequest request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }
        
        User user = customUser.getUser();
        log.info("Generate more mockups request from user: {}", user.getEmail());
        
        DeferredResult<MoreMockupsResponse> deferredResult = new DeferredResult<>(300_000L);
        
        // Check user's coin balance
        int availableCoins = coinManagementService.getUserCoins(user);
        if (availableCoins < 1) {
            MoreMockupsResponse errorResponse = new MoreMockupsResponse();
            errorResponse.setStatus("error");
            errorResponse.setMessage("Insufficient coins. You need at least 1 coin to generate more mockups.");
            errorResponse.setMockups(new ArrayList<>());
            deferredResult.setResult(errorResponse);
            return deferredResult;
        }
        
        // Deduct coins
        CoinManagementService.CoinDeductionResult coinResult =
                coinManagementService.deductCoinsForGeneration(user, 1);
        
        if (!coinResult.isSuccess()) {
            MoreMockupsResponse errorResponse = new MoreMockupsResponse();
            errorResponse.setStatus("error");
            errorResponse.setMessage(coinResult.getErrorMessage());
            errorResponse.setMockups(new ArrayList<>());
            deferredResult.setResult(errorResponse);
            return deferredResult;
        }
        
        final boolean isTrialMode = coinResult.isUsedTrialCoins();
        
        // Generate or use provided seed
        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();
        
        byte[] originalImageData = Base64.getDecoder().decode(request.getOriginalImageBase64());
        
        String prompt = mockupPromptGenerationService.generatePromptForReferenceImage(request.getDescription());
        
        mockupGenerationService.generateMoreMockupsFromImage(originalImageData, prompt, seed)
            .thenAccept(mockups -> {
                MoreMockupsResponse response = new MoreMockupsResponse();
                response.setRequestId(request.getOriginalRequestId());
                response.setStatus("success");
                response.setMessage("Successfully generated more mockups");
                response.setMockups(mockups);
                response.setSeed(seed);
                
                // Persist more mockups
                try {
                    mockupPersistenceService.persistMoreMockups(
                            request.getOriginalRequestId(),
                            mockups,
                            user,
                            request.getDescription(),
                            2 // generationIndex for "more mockups"
                    );
                } catch (Exception e) {
                    log.error("Error persisting more mockups", e);
                }
                
                deferredResult.setResult(response);
            })
            .exceptionally(error -> {
                log.error("Error generating more mockups", error);
                
                // Refund coins on error
                try {
                    coinManagementService.refundCoins(user, 1, isTrialMode);
                    log.info("Refunded 1 coin to user {} due to more mockups generation error", user.getEmail());
                } catch (Exception refundException) {
                    log.error("Failed to refund coins to user {}", user.getEmail(), refundException);
                }
                
                MoreMockupsResponse errorResponse = new MoreMockupsResponse();
                errorResponse.setRequestId(request.getOriginalRequestId());
                errorResponse.setStatus("error");
                errorResponse.setMessage("Failed to generate more mockups: " + error.getMessage());
                errorResponse.setMockups(new ArrayList<>());
                deferredResult.setResult(errorResponse);
                return null;
            });
        
        return deferredResult;
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
        } catch (org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
            log.debug("Client disconnected from mockup SSE stream for request: {} - {}", requestId, e.getMessage());
            streamingStateStore.removeEmitter(requestId);
            return false;
        } catch (java.io.IOException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Broken pipe") ||
                    e.getMessage().contains("Connection reset") ||
                    e.getMessage().contains("Socket closed"))) {
                log.debug("Client connection lost for mockup SSE stream for request: {} - {}", requestId, e.getMessage());
                streamingStateStore.removeEmitter(requestId);
                return false;
            } else {
                log.error("I/O error sending mockup SSE update for request: {}", requestId, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception completionError) {
                    log.debug("Error completing emitter after I/O error: {}", completionError.getMessage());
                }
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending mockup SSE update for request: {}", requestId, e);
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
        
        try {
            emitter.send(SseEmitter.event()
                .name("heartbeat")
                .data("ping"));
            return true;
        } catch (Exception e) {
            log.debug("Heartbeat failed for mockup request: {} - {}", requestId, e.getMessage());
            streamingStateStore.removeEmitter(requestId);
            return false;
        }
    }
    
    private boolean isConnectionActive(String requestId) {
        return streamingStateStore.getEmitter(requestId) != null;
    }
    
    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }
    
    private String sanitizeErrorMessage(Exception error) {
        String errorMessage = error.getMessage() != null ? error.getMessage() : error.toString();
        
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
        
        return "Failed to generate mockups. Please try again or contact support if the issue persists.";
    }
}

