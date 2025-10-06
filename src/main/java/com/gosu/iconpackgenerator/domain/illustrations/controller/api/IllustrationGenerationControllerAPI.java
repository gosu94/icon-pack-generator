package com.gosu.iconpackgenerator.domain.illustrations.controller.api;

import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
import com.gosu.iconpackgenerator.domain.illustrations.dto.MoreIllustrationsRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.MoreIllustrationsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequestMapping("/api/illustrations")
public interface IllustrationGenerationControllerAPI {
    
    @PostMapping("/generate")
    CompletableFuture<IllustrationGenerationResponse> generateIllustrations(
        @Valid @RequestBody IllustrationGenerationRequest request,
        @AuthenticationPrincipal OAuth2User principal
    );
    
    @PostMapping("/generate/stream/start")
    ResponseEntity<Map<String, Object>> startStreamingGeneration(
        @Valid @RequestBody IllustrationGenerationRequest request,
        @AuthenticationPrincipal OAuth2User principal
    );
    
    @GetMapping("/generate/stream/{requestId}")
    SseEmitter connectToStream(@PathVariable String requestId);
    
    @GetMapping("/generate/status/{requestId}")
    ResponseEntity<Map<String, Object>> checkGenerationStatus(
        @PathVariable String requestId,
        @AuthenticationPrincipal OAuth2User principal
    );
    
    @PostMapping("/generate/more")
    DeferredResult<MoreIllustrationsResponse> generateMoreIllustrations(
        @RequestBody MoreIllustrationsRequest request,
        @AuthenticationPrincipal OAuth2User principal
    );
}

