package com.gosu.iconpackgenerator.domain.labels.dto;

import lombok.Data;

import java.util.List;

@Data
public class LabelGenerationResponse {

    private String requestId;
    private String status;
    private String message;
    private List<GeneratedLabel> labels;
    private List<ServiceResults> gptResults;
    private Long seed;

    @Data
    public static class GeneratedLabel {
        private String id;
        private String base64Data;
        private String labelText;
        private String serviceSource;
        private Integer generationIndex;
    }

    @Data
    public static class ServiceResults {
        private String serviceName;
        private String status;
        private String message;
        private List<GeneratedLabel> labels;
        private String originalImageBase64;
        private Long generationTimeMs;
        private Integer generationIndex;
    }
}

