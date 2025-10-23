package com.gosu.iconpackgenerator.domain.labels.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.dto.ServiceProgressUpdate;
import com.gosu.iconpackgenerator.domain.labels.controller.api.LabelGenerationControllerAPI;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationResponse;
import com.gosu.iconpackgenerator.domain.labels.service.LabelGenerationService;
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

import java.io.IOException;
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
public class LabelGenerationController implements LabelGenerationControllerAPI {

    private final LabelGenerationService labelGenerationService;
    private final StreamingStateStore streamingStateStore;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2);

    @PreDestroy
    public void cleanup() {
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

    @Override
    @ResponseBody
    public CompletableFuture<LabelGenerationResponse> generateLabels(
            @Valid @RequestBody LabelGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!request.isValid()) {
            throw new IllegalArgumentException("Please provide label text and either a theme or a reference image.");
        }

        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }

        User user = customUser.getUser();
        log.info("Label generation request from user {}", user.getEmail());

        return labelGenerationService.generateLabels(request, user)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.error("Error generating label for user {}", user.getEmail(), error);
                    } else {
                        log.info("Successfully generated label request {}", response.getRequestId());
                    }
                });
    }

    @Override
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startStreamingGeneration(
            @Valid @RequestBody LabelGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!request.isValid()) {
            throw new IllegalArgumentException("Please provide label text and either a theme or a reference image.");
        }

        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }

        User user = customUser.getUser();
        log.info("Starting streaming label generation for user {}", user.getEmail());

        String requestId = UUID.randomUUID().toString();
        streamingStateStore.addRequest(requestId, request);

        CompletableFuture.runAsync(() -> processStreamingGeneration(requestId, request, user));

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        Map<String, Boolean> enabledServices = new HashMap<>();
        enabledServices.put("flux", false);
        enabledServices.put("recraft", false);
        enabledServices.put("photon", false);
        enabledServices.put("gpt", true);
        enabledServices.put("banana", false);
        response.put("enabledServices", enabledServices);

        return ResponseEntity.ok(response);
    }

    @Override
    @ResponseBody
    public SseEmitter connectToStream(@PathVariable String requestId) {
        log.info("Client connecting to label stream for request {}", requestId);
        SseEmitter emitter = new SseEmitter(600_000L);
        streamingStateStore.addEmitter(requestId, emitter);

        emitter.onCompletion(() -> {
            log.debug("Label SSE completed for {}", requestId);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
        });

        emitter.onTimeout(() -> {
            log.warn("Label SSE timeout for {}", requestId);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.complete();
        });

        emitter.onError(error -> {
            log.error("Label SSE error for {}", requestId, error);
            streamingStateStore.removeEmitter(requestId);
            streamingStateStore.removeRequest(requestId);
            emitter.completeWithError(error);
        });

        return emitter;
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
        log.info("Checking label generation status for request {} by user {}", requestId, user.getEmail());

        Map<String, Object> response = new HashMap<>();

        Object storedResponse = streamingStateStore.getResponse(requestId);
        if (storedResponse instanceof LabelGenerationResponse labelResponse) {
            response.put("status", "completed");
            response.put("message", "Generation completed");
            response.put("data", labelResponse);
            return ResponseEntity.ok(response);
        }

        if (streamingStateStore.getRequest(requestId) != null || streamingStateStore.getEmitter(requestId) != null) {
            response.put("status", "in_progress");
            response.put("message", "Generation is still in progress");
            return ResponseEntity.ok(response);
        }

        response.put("status", "not_found");
        response.put("message", "Generation request not found or expired");
        return ResponseEntity.status(404).body(response);
    }

    private void processStreamingGeneration(String requestId, LabelGenerationRequest request, User user) {
        ScheduledFuture<?> heartbeatTask = null;
        try {
            heartbeatTask = startHeartbeat(requestId);
            final ScheduledFuture<?> finalHeartbeatTask = heartbeatTask;

            labelGenerationService.generateLabels(request, requestId, update -> {
                SseEmitter emitter = streamingStateStore.getEmitter(requestId);
                if (emitter == null) {
                    return;
                }

                try {
                    String json = objectMapper.writeValueAsString(update);
                    boolean sent = safeSendSseUpdate(emitter, requestId, update.getEventType(), json);

                    if ("generation_complete".equals(update.getEventType())) {
                        if (finalHeartbeatTask != null && !finalHeartbeatTask.isCancelled()) {
                            finalHeartbeatTask.cancel(false);
                        }
                        if (sent) {
                            try {
                                emitter.complete();
                            } catch (Exception e) {
                                log.debug("Emitter already completed for {}", requestId);
                            }
                        }
                        streamingStateStore.removeRequest(requestId);
                    }
                } catch (Exception e) {
                    log.error("Failed to send label SSE update for {}", requestId, e);
                    if (finalHeartbeatTask != null && !finalHeartbeatTask.isCancelled()) {
                        finalHeartbeatTask.cancel(false);
                    }
                }
            }, user).whenComplete((response, error) -> {
                if (finalHeartbeatTask != null && !finalHeartbeatTask.isCancelled()) {
                    finalHeartbeatTask.cancel(false);
                }

                SseEmitter emitter = streamingStateStore.getEmitter(requestId);

                if (error != null) {
                    log.error("Label generation failed for {}", requestId, error);
                    if (emitter != null) {
                        try {
                            ServiceProgressUpdate errorUpdate = new ServiceProgressUpdate();
                            errorUpdate.setRequestId(requestId);
                            errorUpdate.setEventType("generation_error");
                            errorUpdate.setStatus("error");
                            errorUpdate.setMessage(sanitizeErrorMessage(error));

                            String json = objectMapper.writeValueAsString(errorUpdate);
                            safeSendSseUpdate(emitter, requestId, "generation_error", json);
                            emitter.completeWithError(error);
                        } catch (Exception sendError) {
                            log.error("Failed to send label generation error update for {}", requestId, sendError);
                        }
                    }
                    streamingStateStore.removeRequest(requestId);
                } else if (response != null) {
                    streamingStateStore.addResponse(requestId, response);
                    if (emitter == null) {
                        log.info("Stored label response for {} (client disconnected)", requestId);
                    }
                }

                streamingStateStore.removeEmitter(requestId);
            });

        } catch (Exception e) {
            log.error("Unexpected error while processing streaming label generation {}", requestId, e);
            if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
                heartbeatTask.cancel(false);
            }
            streamingStateStore.removeRequest(requestId);

            SseEmitter emitter = streamingStateStore.getEmitter(requestId);
            if (emitter != null) {
                try {
                    ServiceProgressUpdate errorUpdate = new ServiceProgressUpdate();
                    errorUpdate.setRequestId(requestId);
                    errorUpdate.setEventType("generation_error");
                    errorUpdate.setStatus("error");
                    errorUpdate.setMessage("Label generation failed. Please try again.");

                    String json = objectMapper.writeValueAsString(errorUpdate);
                    safeSendSseUpdate(emitter, requestId, "generation_error", json);
                    emitter.completeWithError(e);
                } catch (Exception sendError) {
                    log.error("Failed to notify client about label generation failure {}", requestId, sendError);
                }
                streamingStateStore.removeEmitter(requestId);
            }
        }
    }

    private ScheduledFuture<?> startHeartbeat(String requestId) {
        return heartbeatScheduler.scheduleAtFixedRate(() -> {
            SseEmitter emitter = streamingStateStore.getEmitter(requestId);
            if (emitter == null) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("keep-alive"));
            } catch (IllegalStateException ignored) {
                streamingStateStore.removeEmitter(requestId);
            } catch (IOException e) {
                log.debug("Heartbeat failed for {}: {}", requestId, e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private boolean safeSendSseUpdate(SseEmitter emitter, String requestId, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IllegalStateException e) {
            log.debug("Emitter already completed for {}", requestId);
            return false;
        } catch (IOException e) {
            log.debug("Failed to send SSE update for {}: {}", requestId, e.getMessage());
            return false;
        }
    }

    private String sanitizeErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Label generation failed. Please try again.";
        }
        String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
        if (message.contains("policy")) {
            return "Request rejected due to content policy. Please adjust the label text or theme.";
        }
        if (message.contains("rate limit")) {
            return "Service is temporarily busy. Please try again shortly.";
        }
        return "Failed to generate labels. Please try again.";
    }
}
