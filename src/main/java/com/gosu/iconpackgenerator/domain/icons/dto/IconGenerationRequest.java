package com.gosu.iconpackgenerator.domain.icons.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class IconGenerationRequest {
    
    private String generalDescription;
    
    /**
     * Base64 encoded reference image for style-based generation.
     * When provided, the AI will use this image as a style reference instead of the general description.
     */
    private String referenceImageBase64;
    
    @Min(value = 9, message = "Minimum 9 icons")
    @Max(value = 9, message = "Maximum 9 icons")
    private int iconCount;
    
    private List<String> individualDescriptions;
    
    /**
     * Optional seed for reproducible results. If not provided, a random seed will be generated.
     * The same seed should be used for related requests (second grid, missing icons) to maintain visual consistency.
     */
    private Long seed;
    
    /**
     * Number of independent generations to run per enabled service. Each generation will create
     * a separate 3x3 grid with different seeds. Minimum 1, maximum 2.
     */
    @Min(value = 1, message = "Minimum 1 generation per service")
    @Max(value = 2, message = "Maximum 2 generations per service") 
    private int generationsPerService = 1;

    /**
     * Model preference for the primary generation ("standard" or "pro").
     */
    private String baseModel;

    /**
     * Model preference for the variation generation ("standard" or "pro").
     */
    private String variationModel;

    /**
     * When true, the backend should attempt to enhance the user's general theme prompt
     * before generating icons.
     */
    private boolean enhancePrompt;
    
    /**
     * Custom validation to ensure either generalDescription or referenceImageBase64 is provided
     */
    public boolean isValid() {
        return (generalDescription != null && !generalDescription.trim().isEmpty()) || 
               (referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty());
    }
    
    /**
     * Check if this request uses a reference image instead of text description
     */
    public boolean hasReferenceImage() {
        return referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty();
    }
}
