package com.gosu.icon_pack_generator.dto;

import lombok.Data;

import java.util.List;

@Data
public class IconGenerationResponse {
    
    private String status;
    private String message;
    private List<GeneratedIcon> icons;
    private String requestId;
    private ServiceResults falAiResults;
    private ServiceResults recraftResults;
    private ServiceResults photonResults;
    
    @Data
    public static class GeneratedIcon {
        private String id;
        private String base64Data;
        private String description;
        private int gridPosition;
        private String serviceSource; // "flux", "recraft", or "photon"
    }
    
    @Data
    public static class ServiceResults {
        private String serviceName;
        private String status; // "success", "error", "pending"
        private String message;
        private List<GeneratedIcon> icons;
        private long generationTimeMs;
    }
}
