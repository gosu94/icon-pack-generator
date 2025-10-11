package com.gosu.iconpackgenerator.domain.mockups.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockupImageProcessingService {

    public static final int MOCKUP_TARGET_WIDTH = 1920; // Full HD width for 16:9 mockups

    /**
     * Process mockup image - only convert to base64, no cropping needed
     * Mockups are generated in 16:9 aspect ratio and don't need grid cropping
     * 
     * @param imageData The original image as byte array
     * @return Base64 encoded mockup image
     */
    public String processMockupImage(byte[] imageData) {
        try {
            if (imageData == null || imageData.length == 0) {
                log.error("Image data is null or empty");
                throw new RuntimeException("Image data is null or empty");
            }

            log.info("Processing mockup image data of size: {} bytes", imageData.length);
            long startTime = System.currentTimeMillis();

            // Parse image
            BufferedImage mockupImage = ImageIO.read(new ByteArrayInputStream(imageData));
            
            if (mockupImage == null) {
                log.error("Failed to parse image data - ImageIO.read() returned null");
                throw new RuntimeException("Failed to parse image data");
            }

            log.info("Successfully parsed mockup image: {}x{} pixels", 
                    mockupImage.getWidth(), mockupImage.getHeight());

            // Convert to base64
            String base64Mockup = bufferedImageToBase64(mockupImage);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed mockup in {}ms", processingTime);

            return base64Mockup;

        } catch (IOException e) {
            log.error("Error processing mockup image", e);
            throw new RuntimeException("Failed to process mockup image", e);
        }
    }
    
    /**
     * Convert BufferedImage to base64 string
     */
    private String bufferedImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
}

