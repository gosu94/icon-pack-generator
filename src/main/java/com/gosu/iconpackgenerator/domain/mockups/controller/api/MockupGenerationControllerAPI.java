package com.gosu.iconpackgenerator.domain.mockups.controller.api;

import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse;
import com.gosu.iconpackgenerator.domain.mockups.dto.MoreMockupsRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MoreMockupsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequestMapping("/api/mockups")
public interface MockupGenerationControllerAPI {
    
    @PostMapping("/generate")
    CompletableFuture<MockupGenerationResponse> generateMockups(
        @Valid @RequestBody MockupGenerationRequest request,
        @AuthenticationPrincipal OAuth2User principal
    );
    
    @PostMapping("/generate/stream/start")
    ResponseEntity<Map<String, Object>> startStreamingGeneration(
        @Valid @RequestBody MockupGenerationRequest request,
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
    DeferredResult<MoreMockupsResponse> generateMoreMockups(
        @RequestBody MoreMockupsRequest request,
        @AuthenticationPrincipal OAuth2User principal
    );
}

