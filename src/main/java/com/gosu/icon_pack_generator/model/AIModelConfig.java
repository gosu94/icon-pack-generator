package com.gosu.icon_pack_generator.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fal.ai")
public class AIModelConfig {
    
    private String apiKey;
    private String modelEndpoint = "fal-ai/flux/dev";
    private int timeoutSeconds = 120;
    private int maxRetries = 3;
    private String aspectRatio = "1:1";
    private int numImages = 1;
    private boolean enableSafetyChecker = true;
    private String outputFormat = "jpeg";
    private String safetyTolerance = "2";
}
