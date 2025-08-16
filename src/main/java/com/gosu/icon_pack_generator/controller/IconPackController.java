package com.gosu.icon_pack_generator.controller;

import com.gosu.icon_pack_generator.dto.IconExportRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationResponse;
import com.gosu.icon_pack_generator.config.AIServicesConfig;
import com.gosu.icon_pack_generator.service.FalAiModelService;
import com.gosu.icon_pack_generator.service.OpenAiModelService;
import com.gosu.icon_pack_generator.service.RecraftModelService;
import com.gosu.icon_pack_generator.service.IconExportService;
import com.gosu.icon_pack_generator.service.IconGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.ArrayList;
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
    private final OpenAiModelService openAiModelService;
    private final RecraftModelService recraftModelService;
    private final AIServicesConfig aiServicesConfig;
    
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
    
    @GetMapping("/health/openai")
    @ResponseBody
    public CompletableFuture<ResponseEntity<Map<String, Object>>> checkOpenAiHealth() {
        log.info("Checking OpenAI API health");
        
        if (!aiServicesConfig.isOpenAiEnabled()) {
            Map<String, Object> health = new HashMap<>();
            health.put("service", "openai");
            health.put("status", "DISABLED");
            health.put("enabled", false);
            health.put("available", false);
            health.put("model", openAiModelService.getModelName());
            health.put("timestamp", System.currentTimeMillis());
            health.put("message", "OpenAI service is disabled in configuration");
            return CompletableFuture.completedFuture(ResponseEntity.ok(health));
        }
        
        return openAiModelService.testConnection()
                .thenApply(isConnected -> {
                    Map<String, Object> health = new HashMap<>();
                    health.put("service", "openai");
                    health.put("status", isConnected ? "UP" : "DOWN");
                    health.put("enabled", true);
                    health.put("available", openAiModelService.isAvailable());
                    health.put("model", openAiModelService.getModelName());
                    health.put("timestamp", System.currentTimeMillis());
                    
                    if (isConnected) {
                        health.put("message", "OpenAI API is responding correctly");
                        return ResponseEntity.ok(health);
                    } else {
                        health.put("message", "OpenAI API connection failed. Check logs for details.");
                        return ResponseEntity.status(503).body(health);
                    }
                })
                .exceptionally(error -> {
                    log.error("Error during OpenAI health check", error);
                    Map<String, Object> health = new HashMap<>();
                    health.put("service", "openai");
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
        
        CompletableFuture<Boolean> openAiFuture = aiServicesConfig.isOpenAiEnabled() ? 
                openAiModelService.testConnection() : CompletableFuture.completedFuture(false);
        
        CompletableFuture<Boolean> recraftFuture = aiServicesConfig.isRecraftEnabled() ? 
                recraftModelService.testConnection() : CompletableFuture.completedFuture(false);
        
        return CompletableFuture.allOf(falAiFuture, openAiFuture, recraftFuture)
                .thenApply(v -> {
                    boolean falAiStatus = falAiFuture.join();
                    boolean openAiStatus = openAiFuture.join();
                    boolean recraftStatus = recraftFuture.join();
                    
                    Map<String, Object> health = new HashMap<>();
                    health.put("timestamp", System.currentTimeMillis());
                    
                    int enabledCount = (aiServicesConfig.isFluxAiEnabled() ? 1 : 0) +
                                     (aiServicesConfig.isOpenAiEnabled() ? 1 : 0) + 
                                     (aiServicesConfig.isRecraftEnabled() ? 1 : 0);
                    
                    int successCount = (falAiStatus ? 1 : 0) + (openAiStatus ? 1 : 0) + (recraftStatus ? 1 : 0);
                    
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
                    
                    Map<String, Object> openAiHealth = new HashMap<>();
                    openAiHealth.put("enabled", aiServicesConfig.isOpenAiEnabled());
                    openAiHealth.put("status", aiServicesConfig.isOpenAiEnabled() ? 
                            (openAiStatus ? "UP" : "DOWN") : "DISABLED");
                    openAiHealth.put("available", aiServicesConfig.isOpenAiEnabled() && openAiModelService.isAvailable());
                    openAiHealth.put("model", openAiModelService.getModelName());
                    
                    Map<String, Object> recraftHealth = new HashMap<>();
                    recraftHealth.put("enabled", aiServicesConfig.isRecraftEnabled());
                    recraftHealth.put("status", aiServicesConfig.isRecraftEnabled() ? 
                            (recraftStatus ? "UP" : "DOWN") : "DISABLED");
                    recraftHealth.put("available", aiServicesConfig.isRecraftEnabled() && recraftModelService.isAvailable());
                    recraftHealth.put("model", recraftModelService.getModelName());
                    
                    health.put("falAi", falAiHealth);
                    health.put("openAi", openAiHealth);
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
}
