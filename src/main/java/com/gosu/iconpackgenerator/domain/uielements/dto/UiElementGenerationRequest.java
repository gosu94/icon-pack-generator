package com.gosu.iconpackgenerator.domain.uielements.dto;

import lombok.Data;

@Data
public class UiElementGenerationRequest {

    private String referenceImageBase64;

    private Long seed;

    public boolean hasReferenceImage() {
        return referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty();
    }

    public boolean isValid() {
        return hasReferenceImage();
    }
}
