package com.gosu.iconpackgenerator.domain.illustrations.dto;

import lombok.Data;

import java.util.List;

@Data
public class MoreIllustrationsResponse {
    
    /**
     * Status of the generation (success, error)
     */
    private String status;
    
    /**
     * Message describing the result
     */
    private String message;
    
    /**
     * The service that generated the illustrations (always "banana")
     */
    private String serviceName;
    
    /**
     * Time taken for generation in milliseconds
     */
    private long generationTimeMs;
    
    /**
     * The newly generated illustrations
     */
    private List<IllustrationGenerationResponse.GeneratedIllustration> newIllustrations;
    
    /**
     * The original request ID for tracking
     */
    private String originalRequestId;
}

