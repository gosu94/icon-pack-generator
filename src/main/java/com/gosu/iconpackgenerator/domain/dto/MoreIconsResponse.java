package com.gosu.iconpackgenerator.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class MoreIconsResponse {
    
    /**
     * Status of the generation (success, error)
     */
    private String status;
    
    /**
     * Message describing the result
     */
    private String message;
    
    /**
     * The service that generated the icons
     */
    private String serviceName;
    
    /**
     * Time taken for generation in milliseconds
     */
    private long generationTimeMs;
    
    /**
     * The newly generated icons
     */
    private List<IconGenerationResponse.GeneratedIcon> newIcons;
    
    /**
     * The original request ID for tracking
     */
    private String originalRequestId;
}
