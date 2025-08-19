package com.gosu.icon_pack_generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.services")
@Data
public class AIServicesConfig {
    
    private ServiceEnabled fluxAi = new ServiceEnabled();
    private ServiceEnabled recraft = new ServiceEnabled();
    private ServiceEnabled photon = new ServiceEnabled();
    private ServiceEnabled gpt = new ServiceEnabled();
    private ServiceEnabled imagen = new ServiceEnabled();
    
    @Data
    public static class ServiceEnabled {
        private boolean enabled = true;
    }
    
    public boolean isFluxAiEnabled() {
        return fluxAi.isEnabled();
    }
    
    public boolean isRecraftEnabled() {
        return recraft.isEnabled();
    }
    
    public boolean isPhotonEnabled() {
        return photon.isEnabled();
    }
    
    public boolean isGptEnabled() {
        return gpt.isEnabled();
    }
    
    public boolean isImagenEnabled() {
        return imagen.isEnabled();
    }
}
