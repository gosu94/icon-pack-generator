package com.gosu.icon_pack_generator.controller;

import com.gosu.icon_pack_generator.config.AIServicesConfig;
import com.gosu.icon_pack_generator.controller.api.IconPackControllerAPI;
import com.gosu.icon_pack_generator.dto.IconExportRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationResponse;
import com.gosu.icon_pack_generator.dto.MissingIconsRequest;
import com.gosu.icon_pack_generator.dto.MissingIconsResponse;
import com.gosu.icon_pack_generator.dto.ServiceProgressUpdate;
import com.gosu.icon_pack_generator.service.BackgroundRemovalService;
import com.gosu.icon_pack_generator.service.FluxModelService;
import com.gosu.icon_pack_generator.service.GptModelService;
import com.gosu.icon_pack_generator.service.IconExportService;
import com.gosu.icon_pack_generator.service.IconGenerationService;
import com.gosu.icon_pack_generator.service.ImageProcessingService;
import com.gosu.icon_pack_generator.service.ImagenModelService;
import com.gosu.icon_pack_generator.service.PhotonModelService;
import com.gosu.icon_pack_generator.service.PromptGenerationService;
import com.gosu.icon_pack_generator.service.RecraftModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@Slf4j
public class IconPackController implements IconPackControllerAPI {

    private final IconGenerationService iconGenerationService;
    private final IconExportService iconExportService;
    private final FluxModelService fluxModelService;
    private final RecraftModelService recraftModelService;
    private final ImagenModelService imagenModelService;
    private final PhotonModelService photonModelService;
    private final GptModelService gptModelService;
    private final PromptGenerationService promptGenerationService;
    private final ImageProcessingService imageProcessingService;
    private final AIServicesConfig aiServicesConfig;
    private final BackgroundRemovalService backgroundRemovalService;
    private final ObjectMapper objectMapper;

    @Override
    public String index(Model model) {
        model.addAttribute("iconRequest", new IconGenerationRequest());
        return "index";
    }

    @Override
    public CompletableFuture<IconGenerationResponse> generateIcons(@Valid @RequestBody IconGenerationRequest request) {
        log.info("Received icon generation request for {} icons", request.getIconCount());

        // Ensure individual descriptions list is properly sized
        if (request.getIndividualDescriptions() == null) {
            request.setIndividualDescriptions(new ArrayList<>());
        }

        // Pad the list with empty strings if needed
        while (request.getIndividualDescriptions().size() < request.getIconCount()) {
            request.getIndividualDescriptions().add("");
        }

        return iconGenerationService.generateIcons(request)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.error("Error generating icons", error);
                    } else {
                        log.info("Successfully generated icons for request: {}", response.getRequestId());
                    }
                });
    }

    @Override
    public ResponseEntity<Map<String, Object>> startStreamingGeneration(@Valid @RequestBody IconGenerationRequest request) {
        log.info("Starting streaming icon generation for {} icons", request.getIconCount());

        // Generate a unique request ID for this generation
        String requestId = UUID.randomUUID().toString();

        // Store the request for the SSE endpoint to pick up
        // In a real application, you'd use a proper cache/storage mechanism
        streamingRequests.put(requestId, request);

        // Start the generation process asynchronously
        CompletableFuture.runAsync(() -> {
            processStreamingGeneration(requestId, request);
        });

        // Return the request ID and enabled services for the client to connect to SSE
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        // Include which services are enabled so frontend knows which sections to create
        Map<String, Boolean> enabledServices = new HashMap<>();
        enabledServices.put("flux", aiServicesConfig.isFluxAiEnabled());
        enabledServices.put("recraft", aiServicesConfig.isRecraftEnabled());
        enabledServices.put("photon", aiServicesConfig.isPhotonEnabled());
        enabledServices.put("gpt", aiServicesConfig.isGptEnabled());
        enabledServices.put("imagen", aiServicesConfig.isImagenEnabled());
        response.put("enabledServices", enabledServices);

        return ResponseEntity.ok(response);
    }

    // Temporary storage for streaming requests - in production use Redis or similar
    private final Map<String, IconGenerationRequest> streamingRequests = new HashMap<>();
    private final Map<String, SseEmitter> activeEmitters = new HashMap<>();

    @Override
    public SseEmitter connectToStream(@PathVariable String requestId) {
        log.info("Client connecting to stream for request: {}", requestId);

        // Create SSE emitter with 5 minute timeout
        SseEmitter emitter = new SseEmitter(300_000L);

        // Store the emitter for this request
        activeEmitters.put(requestId, emitter);

        // Handle emitter completion and cleanup
        emitter.onCompletion(() -> {
            log.info("SSE completed for request: {}", requestId);
            activeEmitters.remove(requestId);
            streamingRequests.remove(requestId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for request: {}", requestId);
            activeEmitters.remove(requestId);
            streamingRequests.remove(requestId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.error("SSE error for request: {}", requestId, throwable);
            activeEmitters.remove(requestId);
            streamingRequests.remove(requestId);
            emitter.completeWithError(throwable);
        });

        return emitter;
    }

    private void processStreamingGeneration(String requestId, IconGenerationRequest request) {
        try {
            // Ensure individual descriptions list is properly sized
            if (request.getIndividualDescriptions() == null) {
                request.setIndividualDescriptions(new ArrayList<>());
            }

            // Pad the list with empty strings if needed
            while (request.getIndividualDescriptions().size() < request.getIconCount()) {
                request.getIndividualDescriptions().add("");
            }

            // Start generation with progress callback
            iconGenerationService.generateIcons(request, update -> {
                SseEmitter emitter = activeEmitters.get(requestId);
                if (emitter != null) {
                    try {
                        // Send the update as JSON
                        String jsonUpdate = objectMapper.writeValueAsString(update);
                        emitter.send(SseEmitter.event()
                                .name(update.getEventType())
                                .data(jsonUpdate));

                        // Complete the stream when all generation is done
                        if ("generation_complete".equals(update.getEventType())) {
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        log.error("Error sending SSE update for request: {}", requestId, e);
                        emitter.completeWithError(e);
                    }
                }
            }).whenComplete((response, error) -> {
                SseEmitter emitter = activeEmitters.get(requestId);
                if (emitter != null && error != null) {
                    log.error("Error in streaming generation for request: {}", requestId, error);
                    try {
                        // Send error update
                        ServiceProgressUpdate errorUpdate = new ServiceProgressUpdate();
                        errorUpdate.setRequestId(requestId);
                        errorUpdate.setEventType("generation_error");
                        errorUpdate.setStatus("error");
                        errorUpdate.setMessage("Generation failed: " + error.getMessage());

                        String jsonUpdate = objectMapper.writeValueAsString(errorUpdate);
                        emitter.send(SseEmitter.event()
                                .name("generation_error")
                                .data(jsonUpdate));
                    } catch (Exception e) {
                        log.error("Error sending error update for request: {}", requestId, e);
                    }
                    emitter.completeWithError(error);
                }
            });

        } catch (Exception e) {
            log.error("Error in processStreamingGeneration for request: {}", requestId, e);
            SseEmitter emitter = activeEmitters.get(requestId);
            if (emitter != null) {
                emitter.completeWithError(e);
            }
        }
    }

    @Override
    public String generateIconsForm(@Valid IconGenerationRequest request,
                                   BindingResult bindingResult,
                                   Model model) {
        if (bindingResult.hasErrors()) {
            log.warn("Form validation errors: {}", bindingResult.getAllErrors());
            model.addAttribute("iconRequest", request);
            return "index";
        }

        log.info("Received form-based icon generation request for {} icons", request.getIconCount());

        // For form submission, we'll redirect to a processing page
        // and use JavaScript to make the actual API call
        model.addAttribute("iconRequest", request);
        return "generating";
    }

    @Override
    public ResponseEntity<byte[]> exportIcons(@RequestBody IconExportRequest exportRequest) {
        log.info("Received export request for {} icons (background removal: {})",
                exportRequest.getIcons() != null ? exportRequest.getIcons().size() : 0,
                exportRequest.isRemoveBackground());

        try {
            byte[] zipData = iconExportService.createIconPackZip(exportRequest, exportRequest.isRemoveBackground());

            String fileName = "icon-pack-" + exportRequest.getRequestId() + ".zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            log.info("Successfully created ZIP file: {} ({} bytes)", fileName, zipData.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);

        } catch (Exception e) {
            log.error("Error creating icon pack export", e);
            return ResponseEntity.internalServerError()
                    .body("Error creating icon pack".getBytes());
                }
    }

    @Override
    public DeferredResult<MissingIconsResponse> generateMoreIcons(@RequestBody MissingIconsRequest request) {
        log.info("Received generate more icons request for service: {} with {} icon descriptions",
                request.getServiceName(),
                request.getIconDescriptions() != null ? request.getIconDescriptions().size() : 0);

        // Create DeferredResult with 5 minute timeout
        DeferredResult<MissingIconsResponse> deferredResult = new DeferredResult<>(300000L);

        CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                // Validate request
                if (request.getServiceName() == null || request.getServiceName().trim().isEmpty()) {
                    return createErrorResponse(request, "Service name is required", startTime);
                }

                if (request.getOriginalImageBase64() == null || request.getOriginalImageBase64().trim().isEmpty()) {
                    return createErrorResponse(request, "Original image is required", startTime);
                }

                // Icon descriptions are optional - if empty, generate creative variations

                // Convert base64 to byte array
                byte[] originalImageData = Base64.getDecoder().decode(request.getOriginalImageBase64());

                // Generate prompt for second grid (image-to-image)
                String prompt = promptGenerationService.generatePromptFor3x3Grid(
                    request.getGeneralDescription(),
                    request.getIconDescriptions()
                );

                // Get the appropriate service and generate icons
                CompletableFuture<byte[]> generationFuture = getServiceAndGenerate(request.getServiceName(), prompt, originalImageData, request.getSeed());

                byte[] newImageData = generationFuture.join();

                // Process the generated image to extract individual icons
                List<String> base64Icons = imageProcessingService.cropIconsFromGrid(newImageData, 9, true, 0, false);

                // Create icon objects
                List<IconGenerationResponse.GeneratedIcon> newIcons = createIconList(base64Icons, request);

                // Create successful response
                MissingIconsResponse response = new MissingIconsResponse();
                response.setStatus("success");
                response.setMessage("More icons generated successfully with same style");
                response.setServiceName(request.getServiceName());
                response.setNewIcons(newIcons);
                response.setOriginalRequestId(request.getOriginalRequestId());
                response.setGenerationTimeMs(System.currentTimeMillis() - startTime);

                log.info("Successfully generated {} more icons for service: {} in {}ms",
                        newIcons.size(), request.getServiceName(), response.getGenerationTimeMs());

                return response;

            } catch (Exception e) {
                log.error("Error generating more icons for service: {}", request.getServiceName(), e);
                return createErrorResponse(request, "Failed to generate more icons: " + e.getMessage(), startTime);
            }
        }).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Async error in generate more icons", throwable);
                deferredResult.setErrorResult(createErrorResponse(request, "Internal server error: " + throwable.getMessage(), System.currentTimeMillis()));
            } else {
                deferredResult.setResult(result);
            }
        });

        // Set timeout callback
        deferredResult.onTimeout(() -> {
            log.warn("Generate more icons request timed out for service: {}", request.getServiceName());
            deferredResult.setResult(createErrorResponse(request, "Request timed out - generation may still be in progress", System.currentTimeMillis()));
        });

        return deferredResult;
    }

    private CompletableFuture<byte[]> getServiceAndGenerate(String serviceName, String prompt, byte[] originalImageData, Long seed) {
        log.info("Generating more icons with service: {} using seed: {}", serviceName, seed);

        switch (serviceName.toLowerCase()) {
            case "flux":
                if (!aiServicesConfig.isFluxAiEnabled()) {
                    throw new RuntimeException("Flux service is disabled");
                }
                return fluxModelService.generateImageToImage(prompt, originalImageData, seed);

            case "recraft":
                if (!aiServicesConfig.isRecraftEnabled()) {
                    throw new RuntimeException("Recraft service is disabled");
                }
                return recraftModelService.generateImageToImage(prompt, originalImageData, seed);

            case "photon":
                if (!aiServicesConfig.isPhotonEnabled()) {
                    throw new RuntimeException("Photon service is disabled");
                }
                return photonModelService.generateImageToImage(prompt, originalImageData, seed);

            case "gpt":
                if (!aiServicesConfig.isGptEnabled()) {
                    throw new RuntimeException("GPT service is disabled");
                }
                return gptModelService.generateImageToImage(prompt, originalImageData, seed);

            case "imagen":
                if (!aiServicesConfig.isImagenEnabled()) {
                    throw new RuntimeException("Imagen service is disabled");
                }
                return imagenModelService.generateImageToImage(prompt, originalImageData, seed);

            default:
                throw new RuntimeException("Unknown service: " + serviceName);
        }
    }

    private List<IconGenerationResponse.GeneratedIcon> createIconList(List<String> base64Icons, MissingIconsRequest request) {
        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();

        // Process ALL generated icons (should be 9), not just the number of missing descriptions
        for (int i = 0; i < base64Icons.size(); i++) {
            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
            icon.setId(UUID.randomUUID().toString());
            icon.setBase64Data(base64Icons.get(i));

            // Set description if available, otherwise use generic description
            if (request.getIconDescriptions() != null && i < request.getIconDescriptions().size()) {
                icon.setDescription(request.getIconDescriptions().get(i));
            } else {
                icon.setDescription("Generated Icon " + (i + 1));
            }

            icon.setGridPosition(i);
            icon.setServiceSource(request.getServiceName());
            icons.add(icon);
        }

        return icons;
    }

    private MissingIconsResponse createErrorResponse(MissingIconsRequest request, String errorMessage, long startTime) {
        MissingIconsResponse response = new MissingIconsResponse();
        response.setStatus("error");
        response.setMessage(errorMessage);
        response.setServiceName(request.getServiceName());
        response.setNewIcons(new ArrayList<>());
        response.setOriginalRequestId(request.getOriginalRequestId());
        response.setGenerationTimeMs(System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * Background Removal Page
     */
    @Override
    public String backgroundRemovalPage(Model model) {
        // Add service info to the model for display
        model.addAttribute("serviceInfo", backgroundRemovalService.getServiceInfo());
        model.addAttribute("isRembgAvailable", backgroundRemovalService.isRembgAvailable());
        return "background-removal";
    }

    /**
     * Upload and process image with background removal
     */
    @Override
    public ResponseEntity<Map<String, Object>> processBackgroundRemoval(@RequestParam("image") MultipartFile imageFile) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Validate file
            if (imageFile.isEmpty()) {
                response.put("success", false);
                response.put("error", "No file uploaded");
                return ResponseEntity.badRequest().body(response);
            }

            // Check file type
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                response.put("success", false);
                response.put("error", "File must be an image");
                return ResponseEntity.badRequest().body(response);
            }

            // Check file size (limit to 10MB)
            if (imageFile.getSize() > 10 * 1024 * 1024) {
                response.put("success", false);
                response.put("error", "File size must be less than 10MB");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Processing background removal for file: {}, size: {} bytes, type: {}",
                    imageFile.getOriginalFilename(), imageFile.getSize(), contentType);

            // Convert to byte array
            byte[] originalImageData = imageFile.getBytes();

            // Process with background removal
            byte[] processedImageData = backgroundRemovalService.removeBackground(originalImageData);

            // Convert both images to base64 for JSON response
            String originalBase64 = Base64.getEncoder().encodeToString(originalImageData);
            String processedBase64 = Base64.getEncoder().encodeToString(processedImageData);

            // Prepare response
            response.put("success", true);
            response.put("originalImage", "data:" + contentType + ";base64," + originalBase64);
            response.put("processedImage", "data:" + contentType + ";base64," + processedBase64);
            response.put("originalSize", originalImageData.length);
            response.put("processedSize", processedImageData.length);
            response.put("filename", imageFile.getOriginalFilename());
            response.put("rembgAvailable", backgroundRemovalService.isRembgAvailable());

            // Add processing stats
            boolean wasProcessed = !java.util.Arrays.equals(originalImageData, processedImageData);
            response.put("backgroundRemoved", wasProcessed);

            if (wasProcessed) {
                double reductionPercent = 100.0 * (originalImageData.length - processedImageData.length) / originalImageData.length;
                response.put("sizeReduction", String.format("%.1f%%", reductionPercent));
            } else {
                response.put("sizeReduction", "0%");
                if (!backgroundRemovalService.isRembgAvailable()) {
                    response.put("message", "rembg not available - original image returned");
                } else {
                    response.put("message", "Background removal completed");
                }
            }

            log.info("Background removal completed. Original: {} bytes, Processed: {} bytes, Background removed: {}",
                    originalImageData.length, processedImageData.length, wasProcessed);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error processing uploaded file", e);
            response.put("success", false);
            response.put("error", "Error reading uploaded file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (Exception e) {
            log.error("Unexpected error during background removal", e);
            response.put("success", false);
            response.put("error", "Background removal failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Download processed image
     */
    @Override
    public ResponseEntity<byte[]> downloadProcessedImage(@RequestParam("imageData") String base64ImageData,
                                                        @RequestParam("filename") String originalFilename) {
        try {
            // Remove data URL prefix if present
            String base64Data = base64ImageData;
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }

            // Decode base64 to bytes
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);

            // Determine filename
            String downloadFilename = originalFilename;
            if (downloadFilename != null && downloadFilename.contains(".")) {
                String nameWithoutExt = downloadFilename.substring(0, downloadFilename.lastIndexOf('.'));
                String ext = downloadFilename.substring(downloadFilename.lastIndexOf('.'));
                downloadFilename = nameWithoutExt + "_no_bg" + ext;
            } else {
                downloadFilename = "processed_image.png";
            }

            // Set response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", downloadFilename);
            headers.setContentLength(imageBytes.length);

            log.info("Downloading processed image: {} ({} bytes)", downloadFilename, imageBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);

        } catch (Exception e) {
            log.error("Error preparing image download", e);
            return ResponseEntity.status(500).build();
        }
    }
}
