package com.gosu.iconpackgenerator.domain.illustrations.dto;

import lombok.Data;

import java.util.List;

@Data
public class IllustrationGenerationResponse {
    
    private String requestId;
    private String status;
    private String message;
    private List<GeneratedIllustration> illustrations;
    private List<ServiceResults> bananaResults;
    private Long seed;
    private boolean trialMode;
    
    @Data
    public static class ServiceResults {
        private String serviceName;
        private String status;
        private String message;
        private List<GeneratedIllustration> illustrations;
        private String originalGridImageBase64;
        private Long generationTimeMs;
        private Integer generationIndex;
    }
    
    @Data
    public static class GeneratedIllustration {
        private String id;
        private String base64Data;
        private String description;
        private Integer gridPosition;
        private String serviceSource;
    }
}
