package com.gosu.icon_pack_generator.dto;

import lombok.Data;

import java.util.List;

@Data
public class MoreIconsRequest {
    
    /**
     * The original request ID for tracking
     */
    private String originalRequestId;
    
    /**
     * The service to use for generation (flux, recraft, photon, gpt, imagen)
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
     * List of specific icon descriptions for the new grid (can be empty for variations)
     */
    private List<String> iconDescriptions;
    
    /**
     * Total icon count for the new grid (typically 9)
     */
    private int iconCount = 9;
    
    /**
     * The seed from the original generation to maintain visual consistency
     */
    private Long seed;

    /**
     * The generation index from the original request
     */
    private int generationIndex;
}
