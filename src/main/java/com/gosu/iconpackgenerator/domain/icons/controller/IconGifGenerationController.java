package com.gosu.iconpackgenerator.domain.icons.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.dto.GifGenerationRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.GifGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.GifProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.service.GifGenerationService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IconGifGenerationController {

    private final GifGenerationService gifGenerationService;
    private final StreamingStateStore streamingStateStore;
    private final ObjectMapper objectMapper;

    @PostMapping("/api/icons/gif/start")
    @ResponseBody
    public ResponseEntity<GifStartResponse> startGifGeneration(
            @Valid @RequestBody GifGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).build();
        }

        try {
            User user = customUser.getUser();
            GifGenerationService.GifJobContext context = gifGenerationService.prepareJob(request, user);
            String gifRequestId = "gif-" + UUID.randomUUID();

            streamingStateStore.addRequest(gifRequestId, context);

            CompletableFuture<GifGenerationResponse> future = gifGenerationService.processJob(
                    gifRequestId,
                    context,
                    update -> sendProgressUpdate(gifRequestId, update)
            );

            future.whenComplete((response, error) -> handleJobCompletion(gifRequestId, response, error));

            GifStartResponse response = GifStartResponse.started(
                    gifRequestId,
                    context.getSelectedIcons().size(),
                    context.getTotalCost());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            log.warn("Unable to start GIF generation: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(GifStartResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error while starting GIF generation", ex);
            return ResponseEntity.status(500).body(GifStartResponse.error("Failed to start GIF generation."));
        }
    }

    @GetMapping("/api/icons/gif/stream/{gifRequestId}")
    @ResponseBody
    public SseEmitter connectToGifStream(@PathVariable String gifRequestId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        streamingStateStore.addEmitter(gifRequestId, emitter);

        emitter.onCompletion(() -> cleanupEmitter(gifRequestId));
        emitter.onTimeout(() -> cleanupEmitter(gifRequestId));
        emitter.onError(err -> cleanupEmitter(gifRequestId));

        // If the job already finished before the client connected, replay the final result
        GifGenerationResponse cachedResponse = streamingStateStore.getResponse(gifRequestId);
        if (cachedResponse != null) {
            try {
                GifProgressUpdate finalUpdate = GifProgressUpdate.completed(
                        gifRequestId,
                        cachedResponse.getRequestId(),
                        cachedResponse.getGifs(),
                        cachedResponse.getMessage()
                );
                sendEmitterEvent(emitter, finalUpdate);
                emitter.complete();
                cleanupEmitter(gifRequestId);
            } catch (Exception e) {
                log.warn("Failed to replay cached GIF response for {}", gifRequestId, e);
            }
        }

        return emitter;
    }

    private void handleJobCompletion(String gifRequestId, GifGenerationResponse response, Throwable error) {
        if (response != null) {
            streamingStateStore.addResponse(gifRequestId, response);
        }

        if (error != null) {
            log.error("GIF generation job {} completed with error", gifRequestId, error);
            GifGenerationService.GifJobContext context =
                    streamingStateStore.getRequest(gifRequestId);
            String requestId = response != null
                    ? response.getRequestId()
                    : (context != null ? context.getRequest().getRequestId() : gifRequestId);
            GifProgressUpdate failureUpdate = GifProgressUpdate.failed(
                    gifRequestId,
                    requestId,
                    error.getMessage()
            );
            sendProgressUpdate(gifRequestId, failureUpdate);
        }
    }

    private void sendProgressUpdate(String gifRequestId, GifProgressUpdate update) {
        SseEmitter emitter = streamingStateStore.getEmitter(gifRequestId);
        if (emitter == null) {
            log.debug("Emitter not connected yet for gifRequestId: {}", gifRequestId);
            return;
        }

        boolean isTerminal = "gif_complete".equals(update.getEventType())
                || "gif_error".equals(update.getEventType());

        try {
            sendEmitterEvent(emitter, update);
            if (isTerminal) {
                emitter.complete();
                cleanupEmitter(gifRequestId);
            }
        } catch (Exception e) {
            log.warn("Failed to send GIF SSE update for {}", gifRequestId, e);
            emitter.completeWithError(e);
            cleanupEmitter(gifRequestId);
        }
    }

    private void sendEmitterEvent(SseEmitter emitter, GifProgressUpdate update) throws IOException {
        String payload = objectMapper.writeValueAsString(update);
        emitter.send(SseEmitter.event().name(update.getEventType()).data(payload));
    }

    private void cleanupEmitter(String gifRequestId) {
        streamingStateStore.removeEmitter(gifRequestId);
        streamingStateStore.removeRequest(gifRequestId);
        streamingStateStore.removeResponse(gifRequestId);
    }

    @Data
    @AllArgsConstructor
    private static class GifStartResponse {
        private String gifRequestId;
        private int totalIcons;
        private int totalCost;
        private String status;
        private String message;

        static GifStartResponse started(String gifRequestId, int totalIcons, int totalCost) {
            return new GifStartResponse(gifRequestId, totalIcons, totalCost, "started", null);
        }

        static GifStartResponse error(String message) {
            return new GifStartResponse(null, 0, 0, "error", message);
        }
    }
}
