package com.gosu.iconpackgenerator.config;

import com.stripe.Stripe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "stripe")
@Data
@Slf4j
public class StripeConfig {
    
    private String secretKey;
    private String publishableKey;
    private String webhookSecret;
    private Map<String, ProductConfig> products;
    
    @Data
    public static class ProductConfig {
        private String id;
        private String priceId;
        private Integer coins;
    }
    
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("Stripe configuration initialized with {} products", products != null ? products.size() : 0);
    }
}
