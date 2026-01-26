package com.gosu.iconpackgenerator.domain.uielements.controller.api;

import com.gosu.iconpackgenerator.domain.uielements.dto.UiElementGenerationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "UI Element Generation API", description = "Endpoints for generating UI elements")
@RequestMapping("/api/ui-elements")
public interface UiElementGenerationControllerAPI {

    @Operation(summary = "Start streaming UI element generation")
    @PostMapping("/generate/stream/start")
    @ResponseBody
    ResponseEntity<Map<String, Object>> startStreamingGeneration(
            @Valid @RequestBody UiElementGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Connect to SSE stream for UI element progress updates")
    @GetMapping("/generate/stream/{requestId}")
    @ResponseBody
    SseEmitter connectToStream(@PathVariable String requestId);

    @Operation(summary = "Check UI element generation status")
    @GetMapping("/generate/status/{requestId}")
    @ResponseBody
    ResponseEntity<Map<String, Object>> checkGenerationStatus(@PathVariable String requestId,
                                                              @AuthenticationPrincipal OAuth2User principal);
}
