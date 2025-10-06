package com.gosu.iconpackgenerator.domain.illustrations.dto;

import lombok.Data;

import java.util.List;

@Data
public class MoreIllustrationsRequest {
    private String originalRequestId;
    private String serviceName; // Always "banana" for illustrations
    private List<String> illustrationDescriptions;
    private String generalDescription;
    private String originalImageBase64; // The original 2x2 grid
    private Long seed;
    private int generationIndex; // 1 for original, 2+ for variations
}

