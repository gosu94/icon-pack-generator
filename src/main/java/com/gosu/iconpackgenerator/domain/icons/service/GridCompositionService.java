package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GridCompositionService {
    
    private final FileStorageService fileStorageService;
    
    private static final int ICON_SIZE = 300;
    private static final int GRID_SIZE = 3;
    private static final int ILLUSTRATION_GRID_SIZE = 2;
    private static final int ILLUSTRATION_WIDTH = 600;
    private static final int ILLUSTRATION_HEIGHT = 450; // 4:3 aspect ratio
    private static final int LINE_WIDTH = 2;
    private static final Color GRID_LINE_COLOR = new Color(0, 0, 0, 50); // Semi-transparent black
    
    /**
     * Creates a 3x3 grid composition from a list of icon file paths
     * 
     * @param iconFilePaths List of icon file paths (should have at least 9 icons)
     * @return Base64 encoded PNG image of the composed grid
     */
    public String composeGrid(List<String> iconFilePaths) {
        try {
            log.info("Creating 3x3 grid from {} icons", iconFilePaths.size());
            
            // Take only first 9 icons if more are provided
            List<String> iconsToUse = iconFilePaths.subList(0, Math.min(9, iconFilePaths.size()));
            
            if (iconsToUse.size() < 9) {
                throw new IllegalArgumentException("Need at least 9 icons to create a 3x3 grid");
            }
            
            // Calculate total grid dimensions
            int totalWidth = GRID_SIZE * ICON_SIZE + (GRID_SIZE - 1) * LINE_WIDTH;
            int totalHeight = GRID_SIZE * ICON_SIZE + (GRID_SIZE - 1) * LINE_WIDTH;
            
            // Create the grid image with transparency
            BufferedImage gridImage = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = gridImage.createGraphics();
            
            // Enable antialiasing for better quality
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            
            // Fill with transparent background
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, totalWidth, totalHeight);
            g2d.setComposite(AlphaComposite.SrcOver);
            
            // Place icons in grid
            for (int i = 0; i < 9; i++) {
                int row = i / GRID_SIZE;
                int col = i % GRID_SIZE;
                
                int x = col * (ICON_SIZE + LINE_WIDTH);
                int y = row * (ICON_SIZE + LINE_WIDTH);
                
                try {
                    // Load and resize icon
                    BufferedImage iconImage = loadAndResizeIcon(iconsToUse.get(i));
                    g2d.drawImage(iconImage, x, y, null);
                    
                } catch (Exception e) {
                    log.warn("Failed to load icon {}: {}", iconsToUse.get(i), e.getMessage());
                    // Draw placeholder for missing icon
                    drawPlaceholder(g2d, x, y);
                }
            }
            
            // Draw grid lines
            drawGridLines(g2d, totalWidth, totalHeight);
            
            g2d.dispose();
            
            // Convert to base64
            return bufferedImageToBase64(gridImage);
            
        } catch (Exception e) {
            log.error("Error creating grid composition", e);
            throw new RuntimeException("Failed to create grid composition", e);
        }
    }
    
    /**
     * Load an icon from file path and resize it to standard size
     */
    private BufferedImage loadAndResizeIcon(String filePath) throws IOException {
        // Read the icon using FileStorageService
        byte[] iconBytes = fileStorageService.readIcon(filePath);
        
        // Load as BufferedImage
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(iconBytes));
        if (originalImage == null) {
            throw new IOException("Could not load image from: " + filePath);
        }
        
        // Create resized image with transparency support
        BufferedImage resizedImage = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Scale image to fit icon size while maintaining aspect ratio
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        double scaleX = (double) ICON_SIZE / originalWidth;
        double scaleY = (double) ICON_SIZE / originalHeight;
        double scale = Math.min(scaleX, scaleY); // Use the smaller scale to fit within bounds
        
        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);
        
        // Center the image
        int x = (ICON_SIZE - scaledWidth) / 2;
        int y = (ICON_SIZE - scaledHeight) / 2;
        
        g2d.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return resizedImage;
    }
    
    /**
     * Draw a placeholder for missing icons
     */
    private void drawPlaceholder(Graphics2D g2d, int x, int y) {
        // Draw a light gray background
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(x, y, ICON_SIZE, ICON_SIZE);
        
        // Draw border
        g2d.setColor(new Color(200, 200, 200));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(x, y, ICON_SIZE, ICON_SIZE);
        
        // Draw "?" in center
        g2d.setColor(new Color(150, 150, 150));
        g2d.setFont(new Font("Arial", Font.BOLD, 48));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "?";
        int textX = x + (ICON_SIZE - fm.stringWidth(text)) / 2;
        int textY = y + (ICON_SIZE - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(text, textX, textY);
    }
    
    /**
     * Draw semi-transparent grid lines
     */
    private void drawGridLines(Graphics2D g2d, int totalWidth, int totalHeight) {
        g2d.setColor(GRID_LINE_COLOR);
        g2d.setStroke(new BasicStroke(LINE_WIDTH));
        
        // Draw vertical lines
        for (int i = 1; i < GRID_SIZE; i++) {
            int x = i * (ICON_SIZE + LINE_WIDTH) - LINE_WIDTH / 2;
            g2d.drawLine(x, 0, x, totalHeight);
        }
        
        // Draw horizontal lines
        for (int i = 1; i < GRID_SIZE; i++) {
            int y = i * (ICON_SIZE + LINE_WIDTH) - LINE_WIDTH / 2;
            g2d.drawLine(0, y, totalWidth, y);
        }
    }
    
    /**
     * Creates a 2x2 grid composition from a list of illustration file paths
     * 
     * @param illustrationFilePaths List of illustration file paths (should have at least 4 illustrations)
     * @return Base64 encoded PNG image of the composed grid
     */
    public String composeIllustrationGrid(List<String> illustrationFilePaths) {
        try {
            log.info("Creating 2x2 grid from {} illustrations", illustrationFilePaths.size());

            // Take only first 4 illustrations if more are provided
            List<String> illustrationsToUse = illustrationFilePaths.subList(0, Math.min(4, illustrationFilePaths.size()));

            if (illustrationsToUse.size() < 4) {
                throw new IllegalArgumentException("Need at least 4 illustrations to create a 2x2 grid");
            }

            // Calculate total grid dimensions for 2x2 grid with 4:3 aspect ratio per cell
            int totalWidth = ILLUSTRATION_GRID_SIZE * ILLUSTRATION_WIDTH + (ILLUSTRATION_GRID_SIZE - 1) * LINE_WIDTH;
            int totalHeight = ILLUSTRATION_GRID_SIZE * ILLUSTRATION_HEIGHT + (ILLUSTRATION_GRID_SIZE - 1) * LINE_WIDTH;

            // Create the grid image with transparency
            BufferedImage gridImage = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = gridImage.createGraphics();

            // Enable antialiasing for better quality
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Fill with white background
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, totalWidth, totalHeight);

            // Place illustrations in 2x2 grid
            for (int i = 0; i < 4; i++) {
                int row = i / ILLUSTRATION_GRID_SIZE;
                int col = i % ILLUSTRATION_GRID_SIZE;

                int x = col * (ILLUSTRATION_WIDTH + LINE_WIDTH);
                int y = row * (ILLUSTRATION_HEIGHT + LINE_WIDTH);

                try {
                    // Load and resize illustration
                    BufferedImage illustrationImage = loadAndResizeIllustration(illustrationsToUse.get(i));
                    g2d.drawImage(illustrationImage, x, y, null);

                } catch (Exception e) {
                    log.warn("Failed to load illustration {}: {}", illustrationsToUse.get(i), e.getMessage());
                    // Draw placeholder for missing illustration
                    drawIllustrationPlaceholder(g2d, x, y);
                }
            }

            g2d.dispose();

            // Convert to base64
            return bufferedImageToBase64(gridImage);

        } catch (Exception e) {
            log.error("Error creating illustration grid composition", e);
            throw new RuntimeException("Failed to create illustration grid composition", e);
        }
    }
    
    /**
     * Load an illustration from file path and resize it to standard size (4:3 aspect ratio)
     */
    private BufferedImage loadAndResizeIllustration(String filePath) throws IOException {
        // Read the illustration using FileStorageService
        byte[] illustrationBytes = fileStorageService.readIllustration(filePath);
        
        // Load as BufferedImage
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(illustrationBytes));
        if (originalImage == null) {
            throw new IOException("Could not load image from: " + filePath);
        }
        
        // Create resized image with transparency support (4:3 aspect ratio)
        BufferedImage resizedImage = new BufferedImage(ILLUSTRATION_WIDTH, ILLUSTRATION_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Scale image to fit illustration size while maintaining aspect ratio
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        double scaleX = (double) ILLUSTRATION_WIDTH / originalWidth;
        double scaleY = (double) ILLUSTRATION_HEIGHT / originalHeight;
        double scale = Math.min(scaleX, scaleY); // Use the smaller scale to fit within bounds
        
        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);
        
        // Center the image
        int x = (ILLUSTRATION_WIDTH - scaledWidth) / 2;
        int y = (ILLUSTRATION_HEIGHT - scaledHeight) / 2;
        
        g2d.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return resizedImage;
    }
    
    /**
     * Draw a placeholder for missing illustrations
     */
    private void drawIllustrationPlaceholder(Graphics2D g2d, int x, int y) {
        // Draw a light gray background
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(x, y, ILLUSTRATION_WIDTH, ILLUSTRATION_HEIGHT);
        
        // Draw border
        g2d.setColor(new Color(200, 200, 200));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(x, y, ILLUSTRATION_WIDTH, ILLUSTRATION_HEIGHT);
        
        // Draw "?" in center
        g2d.setColor(new Color(150, 150, 150));
        g2d.setFont(new Font("Arial", Font.BOLD, 72));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "?";
        int textX = x + (ILLUSTRATION_WIDTH - fm.stringWidth(text)) / 2;
        int textY = y + (ILLUSTRATION_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(text, textX, textY);
    }
    
    
    /**
     * Convert BufferedImage to base64 PNG string
     */
    private String bufferedImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
}
