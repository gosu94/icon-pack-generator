package com.gosu.iconpackgenerator.domain.mockups.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MockupGenerationRequest {
    
    @Min(1)
    @Max(1)
    private int mockupCount = 1; // Single UI mockup per generation
    
    private String description;
    
    private String referenceImageBase64;
    
    @Min(1)
    @Max(2)
    private int generationsPerService = 1; // 1 for normal, 2 for with variations
    
    private Long seed;
    
    public boolean hasReferenceImage() {
        return referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty();
    }
    
    public boolean isValid() {
        return (description != null && !description.trim().isEmpty()) || hasReferenceImage();
    }
}

