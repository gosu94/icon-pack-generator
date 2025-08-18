package com.gosu.icon_pack_generator.dto;

import lombok.Data;

import java.util.List;

@Data
public class MissingIconsRequest {
    
    /**
     * The original request ID for tracking
     */
    private String originalRequestId;
    
    /**
     * The service to use for generation (flux, recraft, photon)
     */
    private String serviceName;
    
    /**
     * The original generated image (base64 encoded) to use as reference
     */
    private String originalImageBase64;
    
    /**
     * The general theme/description from the original request
     */
    private String generalDescription;
    
    /**
     * List of specific icon descriptions that the user wants to generate
     */
    private List<String> missingIconDescriptions;
    
    /**
     * Total icon count for the new grid (typically 9)
     */
    private int iconCount = 9;
}
