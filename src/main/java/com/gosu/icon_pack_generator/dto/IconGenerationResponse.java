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
    private ServiceResults gptResults;
    private ServiceResults imagenResults;
    
    /**
     * The seed used for generation. Can be reused for consistent results in related requests.
     */
    private Long seed;
    
    @Data
    public static class GeneratedIcon {
        private String id;
        private String base64Data;
        private String description;
        private int gridPosition;
        private String serviceSource; // "flux", "recraft", "photon", "gpt", or "imagen"
    }
    
    @Data
    public static class ServiceResults {
        private String serviceName;
        private String status; // "success", "error", "pending"
        private String message;
        private List<GeneratedIcon> icons;
        private long generationTimeMs;
        
        /**
         * The original grid image (base64 encoded) before cropping into individual icons.
         * Used for image-to-image generation of missing icons.
         */
        private String originalGridImageBase64;
    }
}
