package com.gosu.icon_pack_generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openai")
@Data
public class OpenAiConfig {
    
    private String apiKey;
    private String model = "gpt-image-1";
    private int timeoutSeconds = 120;
    private int maxRetries = 3;
    private boolean enabled = true;
    
    public String getApiKeyHeader() {
        return "Bearer " + apiKey;
    }
}
