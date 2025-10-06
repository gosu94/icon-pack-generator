package com.gosu.iconpackgenerator.domain.illustrations.dto;

import lombok.Data;

import java.util.List;

@Data
public class IllustrationExportRequest {
    private String requestId;
    private String serviceName; // Always "banana" for illustrations
    private List<IllustrationGenerationResponse.GeneratedIllustration> illustrations;
    private List<String> formats; // e.g., ["png", "webp"]
    private List<Integer> sizes; // e.g., [250, 500, 1000] for widths (heights will be 5:4 ratio)
    private Integer generationIndex = 1;
}

