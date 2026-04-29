package com.gosu.iconpackgenerator.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminTestLabIconResponse {

    private String status;
    private String message;
    private Long seed;
    private boolean referenceImageMode;
    private boolean promptEnhanced;
    private String originalGeneralDescription;
    private String effectiveGeneralDescription;
    private String promptUsed;
    private List<String> individualDescriptions;
    private List<ModelResult> results;

    @Data
    public static class ModelResult {
        private String modelId;
        private String modelLabel;
        private String status;
        private String message;
        private long generationTimeMs;
        private String originalGridImageBase64;
        private List<GeneratedIcon> icons;
    }

    @Data
    public static class GeneratedIcon {
        private String id;
        private String base64Data;
        private String description;
        private int gridPosition;
        private String serviceSource;
    }
}
