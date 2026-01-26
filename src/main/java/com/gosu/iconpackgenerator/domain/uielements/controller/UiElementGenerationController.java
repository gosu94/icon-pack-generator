package com.gosu.iconpackgenerator.domain.uielements.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.status.GenerationStatusService;
import com.gosu.iconpackgenerator.domain.uielements.controller.api.UiElementGenerationControllerAPI;
import com.gosu.iconpackgenerator.domain.uielements.dto.UiElementGenerationRequest;
import com.gosu.iconpackgenerator.domain.uielements.service.UiElementGenerationService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
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
public class UiElementGenerationController implements UiElementGenerationControllerAPI {

    private final UiElementGenerationService uiElementGenerationService;
    private final ObjectMapper objectMapper;
    private final StreamingStateStore streamingStateStore;
    private final GenerationStatusService generationStatusService;

    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2);

    @PreDestroy
    public void cleanup() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
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
    public ResponseEntity<Map<String, Object>> startStreamingGeneration(
            @Valid @RequestBody UiElementGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!request.isValid()) {
            throw new IllegalArgumentException("Reference image is required for UI element generation.");
        }

        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }

        User user = customUser.getUser();
        log.info("Streaming UI element generation request from user: {}", user.getEmail());

        String requestId = UUID.randomUUID().toString();
        streamingStateStore.addRequest(requestId, request);

        CompletableFuture.runAsync(() -> processStreamingGeneration(requestId, request, user));

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        Map<String, Boolean> enabledServices = new HashMap<>();
        enabledServices.put("gpt15", true);
        enabledServices.put("gpt", false);
        enabledServices.put("flux", false);
        enabledServices.put("recraft", false);
        enabledServices.put("photon", false);
        enabledServices.put("banana", false);
        response.put("enabledServices", enabledServices);

        return ResponseEntity.ok(response);
    }

    @Override
    @ResponseBody
    public SseEmitter connectToStream(@PathVariable String requestId) {
        log.info("Client connecting to UI element stream for request: {}", requestId);

        SseEmitter emitter = new SseEmitter(600_000L);
        streamingStateStore.addEmitter(requestId, emitter);

        emitter.onCompletion(() -> {
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
        });
        emitter.onTimeout(() -> {
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.completeWithError(throwable);
        });

        return emitter;
    }

    private void processStreamingGeneration(String requestId, UiElementGenerationRequest request, User user) {
        ScheduledFuture<?> heartbeatTask = null;
        final String trackingId = generationStatusService.markGenerationStart("ui-elements", requestId);

        try {
            if (!request.isValid()) {
                throw new IllegalArgumentException("Reference image is required for UI element generation.");
            }

            heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(
                    () -> {
                        if (!isConnectionActive(requestId)) {
                            log.debug("Connection lost during UI element generation for request: {}", requestId);
                        }
                    },
                    5, 5, TimeUnit.SECONDS
            );

            ScheduledFuture<?> finalHeartbeatTask = heartbeatTask;

            uiElementGenerationService.generateUiElements(request, requestId, update -> {
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
                                emitter.complete();
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to send UI element SSE update", e);
                    }
                }
            }, user).whenComplete((response, error) -> {
                generationStatusService.markGenerationComplete(trackingId);
                streamingStateStore.addResponse(requestId, response);
                if (error != null) {
                    log.error("UI element generation failed for request {}", requestId, error);
                    sendGenerationError(requestId, error.getMessage());
                } else if (response != null && "error".equalsIgnoreCase(response.getStatus())) {
                    sendGenerationError(requestId, response.getMessage());
                }
            });
        } catch (Exception e) {
            generationStatusService.markGenerationComplete(trackingId);
            if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
                heartbeatTask.cancel(false);
            }
            log.error("Error processing UI element generation for request {}", requestId, e);
            sendGenerationError(requestId, e.getMessage());
        }
    }

    private void sendGenerationError(String requestId, String message) {
        SseEmitter emitter = streamingStateStore.getEmitter(requestId);
        if (emitter == null) {
            return;
        }
        try {
            Map<String, Object> errorPayload = new HashMap<>();
            errorPayload.put("requestId", requestId);
            errorPayload.put("status", "error");
            errorPayload.put("message", message);
            String jsonUpdate = objectMapper.writeValueAsString(errorPayload);
            safeSendSseUpdate(emitter, requestId, "generation_error", jsonUpdate);
            emitter.complete();
        } catch (Exception e) {
            log.error("Failed to send UI element error update", e);
        }
    }

    private boolean safeSendSseUpdate(SseEmitter emitter, String requestId, String eventName, String data) {
        if (emitter == null) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
            log.debug("Client disconnected from UI element SSE stream for request: {} - {}", requestId, e.getMessage());
            streamingStateStore.removeEmitter(requestId);
            return false;
        } catch (java.io.IOException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Broken pipe") ||
                    e.getMessage().contains("Connection reset") ||
                    e.getMessage().contains("Socket closed"))) {
                log.debug("Client connection lost for UI element SSE stream for request: {} - {}", requestId, e.getMessage());
                streamingStateStore.removeEmitter(requestId);
                return false;
            }
            log.error("I/O error sending UI element SSE update for request: {}", requestId, e);
            return false;
        } catch (Exception e) {
            log.error("Error sending UI element SSE update for request: {}", requestId, e);
            return false;
        }
    }

    private boolean isConnectionActive(String requestId) {
        return streamingStateStore.getEmitter(requestId) != null;
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
        log.info("Checking UI element generation status for request: {} by user: {}", requestId, user.getEmail());

        Map<String, Object> response = new HashMap<>();
        IconGenerationResponse storedResponse = null;
        try {
            storedResponse = streamingStateStore.getResponse(requestId);
        } catch (ClassCastException e) {
            log.debug("Response {} exists but is of different type", requestId);
        }

        if (storedResponse != null) {
            response.put("status", "completed");
            response.put("message", "Generation completed");
            response.put("data", storedResponse);
            return ResponseEntity.ok(response);
        }

        Object activeRequest = streamingStateStore.getRequest(requestId);
        SseEmitter activeEmitter = streamingStateStore.getEmitter(requestId);

        if (activeRequest != null || activeEmitter != null) {
            response.put("status", "in_progress");
            response.put("message", "Generation is still in progress");
            return ResponseEntity.ok(response);
        }

        response.put("status", "not_found");
        response.put("message", "Generation request not found or expired");
        return ResponseEntity.status(404).body(response);
    }
}
