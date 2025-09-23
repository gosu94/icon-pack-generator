//package com.gosu.iconpackgenerator.config;
//
//import com.gosu.iconpackgenerator.domain.service.BackgroundRemovalService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.actuate.health.Health;
//import org.springframework.boot.actuate.health.HealthIndicator;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class BackgroundRemovalHealthIndicator implements HealthIndicator {
//
//    private final BackgroundRemovalService backgroundRemovalService;
//
//    @Override
//    public Health health() {
//        try {
//            String serviceInfo = backgroundRemovalService.getServiceInfo();
//            boolean isAvailable = backgroundRemovalService.isRembgAvailable();
//
//            Health.Builder healthBuilder = isAvailable ? Health.up() : Health.down();
//
//            return healthBuilder
//                    .withDetail("service", serviceInfo)
//                    .withDetail("rembg_available", isAvailable)
//                    .build();
//
//        } catch (Exception e) {
//            log.warn("Error checking background removal service health", e);
//            return Health.down()
//                    .withDetail("error", e.getMessage())
//                    .withDetail("service", "Background removal service check failed")
//                    .build();
//        }
//    }
//}
