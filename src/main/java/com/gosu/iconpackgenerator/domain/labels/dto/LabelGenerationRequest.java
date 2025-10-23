package com.gosu.iconpackgenerator.domain.labels.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LabelGenerationRequest {

    @NotBlank(message = "Label text must be provided")
    private String labelText;

    /**
     * General theme description that guides the typography, palette, and mood.
     */
    private String generalTheme;

    /**
     * Optional base64 encoded reference image for style matching.
     */
    private String referenceImageBase64;

    private Long seed;

    @Min(value = 1, message = "Minimum 1 generation per service")
    @Max(value = 2, message = "Maximum 2 generations per service")
    private int generationsPerService = 1;

    public boolean isValid() {
        boolean hasTheme = generalTheme != null && !generalTheme.trim().isEmpty();
        boolean hasReference = referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty();
        return labelText != null && !labelText.trim().isEmpty() && (hasTheme || hasReference);
    }

    public boolean hasReferenceImage() {
        return referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty();
    }
}

