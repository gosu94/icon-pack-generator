package com.gosu.iconpackgenerator.domain.labels.controller.api;

import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequestMapping("/api/labels")
public interface LabelGenerationControllerAPI {

    @PostMapping("/generate")
    CompletableFuture<LabelGenerationResponse> generateLabels(
            @Valid @RequestBody LabelGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal
    );

    @PostMapping("/generate/stream/start")
    ResponseEntity<Map<String, Object>> startStreamingGeneration(
            @Valid @RequestBody LabelGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal
    );

    @GetMapping("/generate/stream/{requestId}")
    SseEmitter connectToStream(@PathVariable String requestId);

    @GetMapping("/generate/status/{requestId}")
    ResponseEntity<Map<String, Object>> checkGenerationStatus(
            @PathVariable String requestId,
            @AuthenticationPrincipal OAuth2User principal
    );
}

