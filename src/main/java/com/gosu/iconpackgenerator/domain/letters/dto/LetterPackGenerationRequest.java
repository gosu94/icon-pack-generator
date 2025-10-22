package com.gosu.iconpackgenerator.domain.letters.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LetterPackGenerationRequest {

    /**
     * Optional theme or style description that will be combined with
     * the default alphabet-oriented prompt.
     */
    @Size(max = 1000, message = "General description is too long")
    private String generalDescription;

    /**
     * Base64 encoded reference image used for style transfer.
     * When present, the first pack will be generated with image-to-image mode
     * against this reference.
     */
    private String referenceImageBase64;

    /**
     * Optional seed used to stabilize GPT image generations
     * across the three batches.
     */
    private Long seed;

    public boolean isValid() {
        return (generalDescription != null && !generalDescription.trim().isEmpty())
                || (referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty());
    }

    public boolean hasReferenceImage() {
        return referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty();
    }
}
