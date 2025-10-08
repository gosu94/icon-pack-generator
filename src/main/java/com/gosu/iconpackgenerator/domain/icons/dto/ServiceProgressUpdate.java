package com.gosu.iconpackgenerator.domain.icons.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceProgressUpdate {
    
    private String requestId;
    private String serviceName; // "flux", "recraft", "photon", "gpt", "banana"
    private String status; // "started", "upscaling", "success", "error", "complete"
    private String message;
    private List<IconGenerationResponse.GeneratedIcon> icons;
    private String originalGridImageBase64;
    private Long generationTimeMs;
    private String eventType; // "service_update", "generation_complete"
    private int generationIndex;
    
    // Static factory methods for different update types
    public static ServiceProgressUpdate serviceStarted(String requestId, String serviceName, int generationIndex) {
        return new ServiceProgressUpdate(requestId, serviceName, "started", 
                "Generation started", null, null, null, "service_update", generationIndex);
    }
    
    public static ServiceProgressUpdate serviceUpscaling(String requestId, String serviceName, int generationIndex) {
        return new ServiceProgressUpdate(requestId, serviceName, "upscaling", 
                "Upscaling...", null, null, null, "service_update", generationIndex);
    }
    
    public static ServiceProgressUpdate serviceCompleted(String requestId, String serviceName, 
            List<IconGenerationResponse.GeneratedIcon> icons, String originalGridImageBase64, Long generationTimeMs, int generationIndex) {
        return new ServiceProgressUpdate(requestId, serviceName, "success", 
                "Generation completed", icons, originalGridImageBase64, generationTimeMs, "service_update", generationIndex);
    }
    
    public static ServiceProgressUpdate serviceFailed(String requestId, String serviceName, 
            String errorMessage, Long generationTimeMs, int generationIndex) {
        return new ServiceProgressUpdate(requestId, serviceName, "error", 
                errorMessage, null, null, generationTimeMs, "service_update", generationIndex);
    }
    
    public static ServiceProgressUpdate allComplete(String requestId, String message) {
        return new ServiceProgressUpdate(requestId, null, "complete", 
                message, null, null, null, "generation_complete", 0);
    }
    
    public static ServiceProgressUpdate allCompleteWithIcons(String requestId, String message, 
            List<IconGenerationResponse.GeneratedIcon> icons) {
        return new ServiceProgressUpdate(requestId, null, "complete", 
                message, icons, null, null, "generation_complete", 0);
    }
}
