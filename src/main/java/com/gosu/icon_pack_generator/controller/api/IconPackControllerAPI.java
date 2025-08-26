package com.gosu.icon_pack_generator.controller.api;

import com.gosu.icon_pack_generator.dto.IconExportRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationResponse;
import com.gosu.icon_pack_generator.dto.MoreIconsRequest;
import com.gosu.icon_pack_generator.dto.MoreIconsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
// Removed Thymeleaf imports
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Tag(name = "Icon Pack Generator API", description = "Endpoints for generating and managing icon packs")
public interface IconPackControllerAPI {

    // Removed Thymeleaf view endpoints - now serving static content from Next.js

    // REST API endpoints (included in Swagger documentation)
    @Operation(summary = "Generate icons asynchronously", description = "Kicks off the icon generation process and returns immediately with a request ID.")
    @PostMapping("/generate")
    @ResponseBody
    CompletableFuture<IconGenerationResponse> generateIcons(@Valid @RequestBody IconGenerationRequest request);

    @Operation(summary = "Start streaming icon generation", description = "Initiates icon generation and provides a request ID for connecting to an SSE stream for progress updates.")
    @PostMapping("/generate-stream")
    @ResponseBody
    ResponseEntity<Map<String, Object>> startStreamingGeneration(@Valid @RequestBody IconGenerationRequest request);

    @Operation(summary = "Connect to SSE stream for progress updates", description = "Connects a client to the Server-Sent Events (SSE) stream for a given generation request.")
    @GetMapping("/stream/{requestId}")
    @ResponseBody
    SseEmitter connectToStream(@PathVariable String requestId);

    @Operation(summary = "Export icons as a ZIP file", description = "Creates and returns a ZIP file containing the generated icons.")
    @PostMapping("/export")
    @ResponseBody
    ResponseEntity<byte[]> exportIcons(@RequestBody IconExportRequest exportRequest);

    @Operation(summary = "Generate additional icons", description = "Generates more icons based on an existing generation request, maintaining the style.")
    @PostMapping("/generate-more")
    @ResponseBody
    DeferredResult<MoreIconsResponse> generateMoreIcons(@RequestBody MoreIconsRequest request);

    @Operation(summary = "Remove background from an image", description = "Upload an image to have its background removed.")
    @PostMapping("/background-removal/process")
    @ResponseBody
    ResponseEntity<Map<String, Object>> processBackgroundRemoval(@RequestParam("image") MultipartFile imageFile);

    @Operation(summary = "Download processed image", description = "Downloads the image with the background removed.")
    @PostMapping("/background-removal/download")
    ResponseEntity<byte[]> downloadProcessedImage(@RequestParam("imageData") String base64ImageData,
                                                  @RequestParam("filename") String originalFilename);
}
