package com.gosu.iconpackgenerator.domain.icons.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request payload for creating animated GIFs from generated icons.
 */
@Data
public class GifGenerationRequest {

    @NotBlank
    private String requestId;

    @NotEmpty
    private List<String> iconIds;

    /**
     * Optional reference to the specific generation index (1 = original, 2+ = variations).
     */
    private Integer generationIndex;

    /**
     * Optional hint describing which service produced the icons (flux, recraft, etc).
     */
    private String serviceName;

    /**
     * Optional user supplied animation prompt to tweak motion instructions.
     */
    private String motionPrompt;
}
