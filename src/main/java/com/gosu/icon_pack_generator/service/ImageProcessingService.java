package com.gosu.icon_pack_generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
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
}
