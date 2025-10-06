package com.gosu.iconpackgenerator.domain.icons.dto;

import lombok.Data;

import java.util.List;

@Data
public class IconGenerationResponse {
    
    private String status;
    private String message;
    private List<GeneratedIcon> icons;
    private String requestId;
    private List<ServiceResults> falAiResults;
    private List<ServiceResults> recraftResults;
    private List<ServiceResults> photonResults;
    private List<ServiceResults> gptResults;
    private List<ServiceResults> bananaResults;
    
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
        private String serviceSource; // "flux", "recraft", "photon", "gpt", or "banana"
    }
    
    @Data
    public static class ServiceResults {
        private String serviceName;
        private String status; // "success", "error", "pending"
        private String message;
        private List<GeneratedIcon> icons;
        private long generationTimeMs;
        private int generationIndex; // Which generation this is (1, 2, 3, etc.)
        
        /**
         * The original grid image (base64 encoded) before cropping into individual icons.
         * Used for image-to-image generation of missing icons.
         */
        private String originalGridImageBase64;
    }
    
    /**
     * @deprecated Use getBananaResults() instead
     */
    @Deprecated
    public List<ServiceResults> getImagenResults() {
        return bananaResults;
    }
    
    /**
     * @deprecated Use setBananaResults() instead
     */
    @Deprecated
    public void setImagenResults(List<ServiceResults> results) {
        this.bananaResults = results;
    }
}
