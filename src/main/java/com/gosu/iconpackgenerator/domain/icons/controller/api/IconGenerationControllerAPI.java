package com.gosu.iconpackgenerator.domain.icons.controller.api;

import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.MoreIconsRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.MoreIconsResponse;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Tag(name = "Icon Generation API", description = "Endpoints for generating icons")
public interface IconGenerationControllerAPI {

    @Operation(summary = "Generate icons asynchronously", description = "Kicks off the icon generation process and returns immediately with a request ID.")
    @PostMapping("/generate")
    @ResponseBody
    CompletableFuture<IconGenerationResponse> generateIcons(@Valid @RequestBody IconGenerationRequest request, @AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Start streaming icon generation", description = "Initiates icon generation and provides a request ID for connecting to an SSE stream for progress updates.")
    @PostMapping("/generate-stream")
    @ResponseBody
    ResponseEntity<Map<String, Object>> startStreamingGeneration(@Valid @RequestBody IconGenerationRequest request, @AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Connect to SSE stream for progress updates", description = "Connects a client to the Server-Sent Events (SSE) stream for a given generation request.")
    @GetMapping("/stream/{requestId}")
    @ResponseBody
    SseEmitter connectToStream(@PathVariable String requestId);

    @Operation(summary = "Generate additional icons", description = "Generates more icons based on an existing generation request, maintaining the style.")
    @PostMapping("/generate-more")
    @ResponseBody
    DeferredResult<MoreIconsResponse> generateMoreIcons(@RequestBody MoreIconsRequest request, @AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Check generation status", description = "Checks if a generation request has completed and returns the results if available.")
    @GetMapping("/status/{requestId}")
    @ResponseBody
    ResponseEntity<Map<String, Object>> checkGenerationStatus(@PathVariable String requestId, @AuthenticationPrincipal OAuth2User principal);
}
