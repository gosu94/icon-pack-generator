package com.gosu.iconpackgenerator.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class AdminTestLabIconRequest {

    private String generalDescription;
    private String referenceImageBase64;

    @Min(value = 9, message = "Minimum 9 icons")
    @Max(value = 9, message = "Maximum 9 icons")
    private int iconCount = 9;

    private List<String> individualDescriptions;
    private boolean enhancePrompt;
    private Long seed;
    private boolean runGpt = true;
    private boolean runGpt15 = true;
    private boolean runGpt2 = true;

    public boolean isValid() {
        return (generalDescription != null && !generalDescription.trim().isEmpty())
                || (referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty());
    }

    public boolean hasReferenceImage() {
        return referenceImageBase64 != null && !referenceImageBase64.trim().isEmpty();
    }

    public boolean hasAnyModelSelected() {
        return runGpt || runGpt15 || runGpt2;
    }
}
