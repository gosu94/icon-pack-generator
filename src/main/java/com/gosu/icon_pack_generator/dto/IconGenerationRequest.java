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
    
    /**
     * Number of independent generations to run per enabled service. Each generation will create
     * a separate 3x3 grid with different seeds. Minimum 1, maximum 2.
     */
    @Min(value = 1, message = "Minimum 1 generation per service")
    @Max(value = 2, message = "Maximum 2 generations per service") 
    private int generationsPerService = 1;
}
