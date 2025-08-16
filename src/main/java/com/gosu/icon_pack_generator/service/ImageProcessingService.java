package com.gosu.icon_pack_generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Iterator;

@Service
@Slf4j
public class ImageProcessingService {
    
    /**
     * Crop a 3x3 grid of icons from the generated image
     * @param imageData The original image as byte array
     * @param iconCount The number of icons to extract (9 or 18)
     * @return List of cropped icon images as base64 strings
     */
    public List<String> cropIconsFromGrid(byte[] imageData, int iconCount) {
        try {
            // Add validation and logging
            if (imageData == null) {
                log.error("Image data is null");
                throw new RuntimeException("Image data is null");
            }
            
            if (imageData.length == 0) {
                log.error("Image data is empty");
                throw new RuntimeException("Image data is empty");
            }
            
            log.info("Processing image data of size: {} bytes", imageData.length);
            
            BufferedImage originalImage = null;
            
            // Check if it's WebP format for better logging
            if (isWebPFormat(imageData)) {
                log.info("WebP format detected. Attempting to decode using TwelveMonkeys ImageIO WebP support...");
                originalImage = readImageWithWebPSupport(imageData);
            } else {
                log.debug("Standard image format detected, using regular ImageIO...");
                originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            }
            
            if (originalImage == null) {
                log.error("Failed to parse image data - ImageIO.read() returned null. Data size: {} bytes", imageData.length);
                // Log first few bytes to help debug
                if (imageData.length > 10) {
                    byte[] firstBytes = java.util.Arrays.copyOf(imageData, 10);
                    log.error("First 10 bytes of image data: {}", java.util.Arrays.toString(firstBytes));
                }
                throw new RuntimeException("Failed to parse image data - ImageIO returned null");
            }
            
            log.info("Successfully parsed image: {}x{} pixels", originalImage.getWidth(), originalImage.getHeight());
            
            List<String> croppedIcons = new ArrayList<>();
            
            if (iconCount == 9) {
                croppedIcons.addAll(cropGrid3x3(originalImage));
            } else if (iconCount == 18) {
                // For 18 icons, we'll need to generate two 3x3 grids
                // For now, we'll crop first 9 from one grid
                croppedIcons.addAll(cropGrid3x3(originalImage));
                // TODO: Handle second grid generation for 18 icons
            }
            
            log.info("Successfully cropped {} icons from grid", croppedIcons.size());
            return croppedIcons;
            
        } catch (IOException e) {
            log.error("Error processing image", e);
            throw new RuntimeException("Failed to process image", e);
        }
    }
    
    private List<String> cropGrid3x3(BufferedImage originalImage) throws IOException {
        List<String> icons = new ArrayList<>();
        
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        int iconWidth = width / 3;
        int iconHeight = height / 3;
        
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = col * iconWidth;
                int y = row * iconHeight;
                
                BufferedImage croppedIcon = originalImage.getSubimage(x, y, iconWidth, iconHeight);
                String base64Icon = bufferedImageToBase64(croppedIcon);
                icons.add(base64Icon);
                
                log.debug("Cropped icon at position [{},{}] with size {}x{}", row, col, iconWidth, iconHeight);
            }
        }
        
        return icons;
    }
    
    private String bufferedImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
    
    /**
     * Check if the image data is in WebP format
     */
    private boolean isWebPFormat(byte[] imageData) {
        if (imageData == null || imageData.length < 12) {
            return false;
        }
        
        // Check for RIFF header (first 4 bytes: "RIFF")
        boolean isRIFF = imageData[0] == 0x52 && imageData[1] == 0x49 && 
                        imageData[2] == 0x46 && imageData[3] == 0x46;
        
        if (!isRIFF) {
            return false;
        }
        
        // Check for WEBP signature (bytes 8-11: "WEBP")
        boolean isWEBP = imageData[8] == 0x57 && imageData[9] == 0x45 && 
                        imageData[10] == 0x42 && imageData[11] == 0x50;
        
        if (isWEBP) {
            log.debug("WebP format detected in image data (RIFF/WEBP signature found)");
            return true;
        }
        
        return false;
    }
    
    /**
     * Read image with explicit WebP support using TwelveMonkeys ImageIO
     */
    private BufferedImage readImageWithWebPSupport(byte[] imageData) throws IOException {
        try {
            // First, try the standard ImageIO.read (should work with TwelveMonkeys installed)
            ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);
            BufferedImage image = ImageIO.read(inputStream);
            
            if (image != null) {
                log.info("Successfully read WebP image: {}x{} pixels", image.getWidth(), image.getHeight());
                return image;
            }
            
            // If that fails, try to explicitly find WebP readers
            log.info("Standard ImageIO.read() failed for WebP, trying explicit WebP readers...");
            inputStream = new ByteArrayInputStream(imageData);
            
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(ImageIO.createImageInputStream(inputStream));
                    BufferedImage webpImage = reader.read(0);
                    log.info("Successfully read WebP using explicit reader: {}x{} pixels", 
                            webpImage.getWidth(), webpImage.getHeight());
                    return webpImage;
                } finally {
                    reader.dispose();
                }
            } else {
                log.error("No WebP ImageReader found. TwelveMonkeys ImageIO WebP support may not be properly installed.");
                throw new IOException("No WebP ImageReader available");
            }
            
        } catch (Exception e) {
            log.error("Error reading WebP image", e);
            throw new IOException("Failed to read WebP image: " + e.getMessage(), e);
        }
    }
}
