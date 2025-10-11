package com.gosu.iconpackgenerator.domain.mockups.dto;

import lombok.Data;

import java.util.List;

@Data
public class MockupGenerationResponse {
    
    private String requestId;
    private String status;
    private String message;
    private List<GeneratedMockup> mockups;
    private List<ServiceResults> bananaResults;
    private Long seed;
    
    @Data
    public static class ServiceResults {
        private String serviceName;
        private String status;
        private String message;
        private List<GeneratedMockup> mockups;
        private String originalImageBase64;
        private Long generationTimeMs;
        private Integer generationIndex;
    }
    
    @Data
    public static class GeneratedMockup {
        private String id;
        private String base64Data;
        private String description;
        private String serviceSource;
    }
}

