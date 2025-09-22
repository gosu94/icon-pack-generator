package com.gosu.iconpackgenerator.service

import com.gosu.iconpackgenerator.domain.service.BackgroundRemovalService
import com.gosu.iconpackgenerator.domain.service.ImageProcessingService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Test configuration for ImageProcessingService tests.
 * Only loads the necessary beans to avoid Spring Boot context loading issues.
 */
@TestConfiguration
class ImageProcessingServiceTestConfig {

    @Bean
    @Primary
    BackgroundRemovalService backgroundRemovalService() {
        // Create a simple test implementation that returns input unchanged
        return new BackgroundRemovalService() {
            @Override
            byte[] removeBackground(byte[] imageData) {
                // Return the input unchanged for test purposes
                return imageData
            }
        }
    }

    @Bean
    @Primary
    ImageProcessingService imageProcessingService(BackgroundRemovalService backgroundRemovalService) {
        return new ImageProcessingService(backgroundRemovalService)
    }
}
