package com.gosu.icon_pack_generator.controller;

import com.gosu.icon_pack_generator.dto.IconExportRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationResponse;
import com.gosu.icon_pack_generator.config.AIServicesConfig;
import com.gosu.icon_pack_generator.service.FalAiModelService;

import com.gosu.icon_pack_generator.service.RecraftModelService;
import com.gosu.icon_pack_generator.service.IconExportService;
import com.gosu.icon_pack_generator.service.IconGenerationService;
import com.gosu.icon_pack_generator.service.BackgroundRemovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IconPackController {
    
    private final IconGenerationService iconGenerationService;
    private final IconExportService iconExportService;
    private final FalAiModelService falAiModelService;
    private final RecraftModelService recraftModelService;
    private final AIServicesConfig aiServicesConfig;
    private final BackgroundRemovalService backgroundRemovalService;
    
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("iconRequest", new IconGenerationRequest());
        return "index";
    }
    
    @PostMapping("/generate")
    @ResponseBody
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
    
    @PostMapping("/generate-form")
    public String generateIconsForm(@Valid @ModelAttribute IconGenerationRequest request, 
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
    
    @PostMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportIcons(@RequestBody IconExportRequest exportRequest) {
        log.info("Received export request for {} icons", 
                exportRequest.getIcons() != null ? exportRequest.getIcons().size() : 0);
        
        try {
            byte[] zipData = iconExportService.createIconPackZip(exportRequest);
            
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
    
    @GetMapping("/health/fal-ai")
    @ResponseBody
    public CompletableFuture<ResponseEntity<Map<String, Object>>> checkFalAiHealth() {
        log.info("Checking fal.ai API health");
        
        if (!aiServicesConfig.isFluxAiEnabled()) {
            Map<String, Object> health = new HashMap<>();
            health.put("service", "fal.ai");
            health.put("status", "DISABLED");
            health.put("enabled", false);
            health.put("available", false);
            health.put("model", falAiModelService.getModelName());
            health.put("timestamp", System.currentTimeMillis());
            health.put("message", "Fal.ai service is disabled in configuration");
            return CompletableFuture.completedFuture(ResponseEntity.ok(health));
        }
        
        return falAiModelService.testConnection()
                .thenApply(isConnected -> {
                    Map<String, Object> health = new HashMap<>();
                    health.put("service", "fal.ai");
                    health.put("status", isConnected ? "UP" : "DOWN");
                    health.put("enabled", true);
                    health.put("available", falAiModelService.isAvailable());
                    health.put("model", falAiModelService.getModelName());
                    health.put("timestamp", System.currentTimeMillis());
                    
                    if (isConnected) {
                        health.put("message", "Fal.ai API is responding correctly");
                        return ResponseEntity.ok(health);
                    } else {
                        health.put("message", "Fal.ai API connection failed. Check logs for details.");
                        return ResponseEntity.status(503).body(health);
                    }
                })
                .exceptionally(error -> {
                    log.error("Error during fal.ai health check", error);
                    Map<String, Object> health = new HashMap<>();
                    health.put("service", "fal.ai");
                    health.put("status", "ERROR");
                    health.put("enabled", true);
                    health.put("available", false);
                    health.put("error", error.getMessage());
                    health.put("timestamp", System.currentTimeMillis());
                    return ResponseEntity.status(503).body(health);
                });
    }
    

    

    
    @GetMapping("/health/recraft")
    @ResponseBody
    public CompletableFuture<ResponseEntity<Map<String, Object>>> checkRecraftHealth() {
        log.info("Checking Recraft API health");
        
        if (!aiServicesConfig.isRecraftEnabled()) {
            Map<String, Object> health = new HashMap<>();
            health.put("service", "recraft");
            health.put("status", "DISABLED");
            health.put("enabled", false);
            health.put("available", false);
            health.put("model", recraftModelService.getModelName());
            health.put("timestamp", System.currentTimeMillis());
            health.put("message", "Recraft service is disabled in configuration");
            return CompletableFuture.completedFuture(ResponseEntity.ok(health));
        }
        
        return recraftModelService.testConnection()
                .thenApply(isConnected -> {
                    Map<String, Object> health = new HashMap<>();
                    health.put("service", "recraft");
                    health.put("status", isConnected ? "UP" : "DOWN");
                    health.put("enabled", true);
                    health.put("available", recraftModelService.isAvailable());
                    health.put("model", recraftModelService.getModelName());
                    health.put("timestamp", System.currentTimeMillis());
                    
                    if (isConnected) {
                        health.put("message", "Recraft API is responding correctly");
                        return ResponseEntity.ok(health);
                    } else {
                        health.put("message", "Recraft API connection failed. Check logs for details.");
                        return ResponseEntity.status(503).body(health);
                    }
                })
                .exceptionally(error -> {
                    log.error("Error during Recraft health check", error);
                    Map<String, Object> health = new HashMap<>();
                    health.put("service", "recraft");
                    health.put("status", "ERROR");
                    health.put("enabled", true);
                    health.put("available", false);
                    health.put("error", error.getMessage());
                    health.put("timestamp", System.currentTimeMillis());
                    return ResponseEntity.status(503).body(health);
                });
    }
    
    @GetMapping("/health/all")
    @ResponseBody
    public CompletableFuture<ResponseEntity<Map<String, Object>>> checkAllServicesHealth() {
        log.info("Checking health of all AI services including Recraft");
        
        // Only test connections for enabled services
        CompletableFuture<Boolean> falAiFuture = aiServicesConfig.isFluxAiEnabled() ?
                falAiModelService.testConnection() : CompletableFuture.completedFuture(false);
        
        CompletableFuture<Boolean> recraftFuture = aiServicesConfig.isRecraftEnabled() ? 
                recraftModelService.testConnection() : CompletableFuture.completedFuture(false);
        
        return CompletableFuture.allOf(falAiFuture, recraftFuture)
                .thenApply(v -> {
                    boolean falAiStatus = falAiFuture.join();
                    boolean recraftStatus = recraftFuture.join();
                    
                    Map<String, Object> health = new HashMap<>();
                    health.put("timestamp", System.currentTimeMillis());
                    
                    int enabledCount = (aiServicesConfig.isFluxAiEnabled() ? 1 : 0) +
                                     (aiServicesConfig.isRecraftEnabled() ? 1 : 0);
                    
                    int successCount = (falAiStatus ? 1 : 0) + (recraftStatus ? 1 : 0);
                    
                    String overallStatus;
                    if (enabledCount == 0) {
                        overallStatus = "ALL_DISABLED";
                    } else if (successCount == enabledCount) {
                        overallStatus = "UP";
                    } else if (successCount > 0) {
                        overallStatus = "PARTIAL";
                    } else {
                        overallStatus = "DOWN";
                    }
                    
                    health.put("overallStatus", overallStatus);
                    health.put("enabledCount", enabledCount);
                    health.put("successCount", successCount);
                    
                    Map<String, Object> falAiHealth = new HashMap<>();
                    falAiHealth.put("enabled", aiServicesConfig.isFluxAiEnabled());
                    falAiHealth.put("status", aiServicesConfig.isFluxAiEnabled() ?
                            (falAiStatus ? "UP" : "DOWN") : "DISABLED");
                    falAiHealth.put("available", aiServicesConfig.isFluxAiEnabled() && falAiModelService.isAvailable());
                    falAiHealth.put("model", falAiModelService.getModelName());
                    

                    
                    Map<String, Object> recraftHealth = new HashMap<>();
                    recraftHealth.put("enabled", aiServicesConfig.isRecraftEnabled());
                    recraftHealth.put("status", aiServicesConfig.isRecraftEnabled() ? 
                            (recraftStatus ? "UP" : "DOWN") : "DISABLED");
                    recraftHealth.put("available", aiServicesConfig.isRecraftEnabled() && recraftModelService.isAvailable());
                    recraftHealth.put("model", recraftModelService.getModelName());
                    
                    health.put("falAi", falAiHealth);
                    health.put("recraft", recraftHealth);
                    
                    String message;
                    if (enabledCount == 0) {
                        message = "All AI services are disabled in configuration";
                    } else if (successCount == enabledCount) {
                        message = "All enabled AI services are operational";
                    } else if (successCount > 0) {
                        message = successCount + " out of " + enabledCount + " enabled services are operational";
                    } else {
                        message = "All enabled AI services are down";
                    }
                    
                    health.put("message", message);
                    
                    if (enabledCount == 0 || successCount == 0) {
                        return ResponseEntity.status(503).body(health);
                    } else if (successCount == enabledCount) {
                        return ResponseEntity.ok(health);
                    } else {
                        return ResponseEntity.status(207).body(health); // Multi-Status
                    }
                })
                .exceptionally(error -> {
                    log.error("Error during health check", error);
                    Map<String, Object> health = new HashMap<>();
                    health.put("overallStatus", "ERROR");
                    health.put("error", error.getMessage());
                    health.put("timestamp", System.currentTimeMillis());
                    return ResponseEntity.status(503).body(health);
                });
    }
    
    /**
     * Background Removal Page
     */
    @GetMapping("/background-removal")
    public String backgroundRemovalPage(Model model) {
        // Add service info to the model for display
        model.addAttribute("serviceInfo", backgroundRemovalService.getServiceInfo());
        model.addAttribute("isRembgAvailable", backgroundRemovalService.isRembgAvailable());
        return "background-removal";
    }
    
    /**
     * Upload and process image with background removal
     */
    @PostMapping("/background-removal/process")
    @ResponseBody
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
    @PostMapping("/background-removal/download")
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
