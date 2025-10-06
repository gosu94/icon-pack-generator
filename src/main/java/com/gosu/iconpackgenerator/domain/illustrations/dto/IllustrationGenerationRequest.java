package com.gosu.iconpackgenerator.domain.illustrations.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class IllustrationGenerationRequest {
    
    @Min(4)
    @Max(4)
    private int illustrationCount = 4; // Fixed at 4 for 2x2 grid
    
    private String generalDescription;
    
    private List<String> individualDescriptions;
    
    private String referenceImageBase64;
    
    @Min(1)
    @Max(2)
    private int generationsPerService = 1; // 1 for normal, 2 for with variations
    
    private Long seed;
    
    public boolean hasReferenceImage() {
        return referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty();
    }
    
    public boolean isValid() {
        return (generalDescription != null && !generalDescription.trim().isEmpty()) || hasReferenceImage();
    }
}

