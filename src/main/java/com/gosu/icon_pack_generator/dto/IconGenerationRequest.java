package com.gosu.icon_pack_generator.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class IconGenerationRequest {
    
    @NotBlank(message = "General description is required")
    private String generalDescription;
    
    @Min(value = 9, message = "Minimum 9 icons")
    @Max(value = 18, message = "Maximum 18 icons")
    private int iconCount;
    
    private List<String> individualDescriptions;
    
    /**
     * Optional seed for reproducible results. If not provided, a random seed will be generated.
     * The same seed should be used for related requests (second grid, missing icons) to maintain visual consistency.
     */
    private Long seed;
}
