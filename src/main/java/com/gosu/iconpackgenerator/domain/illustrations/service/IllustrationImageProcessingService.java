package com.gosu.iconpackgenerator.domain.illustrations.service;

import com.gosu.iconpackgenerator.domain.icons.service.BackgroundRemovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IllustrationImageProcessingService {
    
    private final BackgroundRemovalService backgroundRemovalService;
    
    public static final int ILLUSTRATION_TARGET_SIZE = 600; // Larger than icons
    
    /**
     * Crop a 2x2 grid of illustrations from the generated image (4:3 aspect ratio)
     * Background removal is MANDATORY for illustrations
     *
     * @param imageData The original image as byte array
     * @return List of cropped illustration images as base64 strings
     */
    public List<String> cropIllustrationsFromGrid(byte[] imageData) {
        return cropIllustrationsFromGrid(imageData, true, 0);
    }
    
    /**
     * Crop a 2x2 grid of illustrations with optional centering
     *
     * @param imageData   The original image as byte array
     * @param centerIllustrations Whether to center the illustrations on their canvas
     * @param targetSize  Target size for centered illustrations
     * @return List of cropped illustration images as base64 strings
     */
    public List<String> cropIllustrationsFromGrid(byte[] imageData, boolean centerIllustrations, int targetSize) {
        try {
            if (imageData == null || imageData.length == 0) {
                log.error("Image data is null or empty");
                throw new RuntimeException("Image data is null or empty");
            }
            
            log.info("Processing illustration image data of size: {} bytes", imageData.length);
            long totalStartTime = System.currentTimeMillis();
            
//            log.info("Removing background from illustration grid image (mandatory step)");
//            byte[] processedImageData = backgroundRemovalService.removeBackground(imageData);
//            log.info("Background removal completed");
            
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            
            if (originalImage == null) {
                log.error("Failed to parse image data - ImageIO.read() returned null");
                throw new RuntimeException("Failed to parse image data");
            }
            
            log.info("Successfully parsed illustration image: {}x{} pixels", 
                originalImage.getWidth(), originalImage.getHeight());
            
            // Detect and remove solid frame artifacts
            BufferedImage frameCleanedImage = detectAndRemoveSolidFrame(originalImage);
            if (frameCleanedImage != originalImage) {
                log.info("Detected and removed solid frame from illustration image");
                originalImage = frameCleanedImage;
            }
            
            List<String> croppedIllustrations = crop2x2Grid(originalImage, centerIllustrations, targetSize);
            
            long totalProcessingTime = System.currentTimeMillis() - totalStartTime;
            log.info("Successfully cropped {} illustrations in {}ms", croppedIllustrations.size(), totalProcessingTime);
            
            return croppedIllustrations;
            
        } catch (IOException e) {
            log.error("Error processing illustration image", e);
            throw new RuntimeException("Failed to process illustration image", e);
        }
    }
    
    /**
     * Crop a 2x2 grid of illustrations
     */
    private List<String> crop2x2Grid(BufferedImage originalImage, boolean centerIllustrations, int targetSize) throws IOException {
        List<String> illustrations = new ArrayList<>();
        
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        log.info("Starting intelligent 2x2 grid cropping for illustration image {}x{}", width, height);
        
        GridBounds gridBounds = detectGridBounds(originalImage);
        
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                Rectangle illustrationRect = gridBounds.getIllustrationRectangle(row, col);
                
                log.debug("Cropping illustration at position [{},{}] with bounds: x={}, y={}, width={}, height={}",
                    row, col, illustrationRect.x, illustrationRect.y, illustrationRect.width, illustrationRect.height);
                
                BufferedImage croppedIllustration = originalImage.getSubimage(
                    illustrationRect.x, illustrationRect.y, illustrationRect.width, illustrationRect.height);
                
                // Optionally center the illustration
                if (centerIllustrations) {
                    int targetWidth, targetHeight;
                    if (targetSize > 0) {
                        // Scale to target size while preserving aspect ratio
                        double aspectRatio = (double) illustrationRect.width / illustrationRect.height;
                        targetWidth = targetSize;
                        targetHeight = (int) (targetSize / aspectRatio);
                    } else {
                        // Use original rectangular dimensions
                        targetWidth = illustrationRect.width;
                        targetHeight = illustrationRect.height;
                    }
                    croppedIllustration = centerIllustration(croppedIllustration, targetWidth, targetHeight);
                    log.debug("Centered illustration at position [{},{}] to size {}x{}", row, col, targetWidth, targetHeight);
                }
                
                String base64Illustration = bufferedImageToBase64(croppedIllustration);
                illustrations.add(base64Illustration);
            }
        }
        
        return illustrations;
    }
    
    /**
     * Helper class to represent intelligent grid bounds for a 2x2 illustration grid
     */
    private static class GridBounds {
        private final int[][] xBounds; // [column][start/end] - x coordinates for each column
        private final int[][] yBounds; // [row][start/end] - y coordinates for each row
        
        public GridBounds(int[][] xBounds, int[][] yBounds) {
            this.xBounds = xBounds;
            this.yBounds = yBounds;
        }
        
        public Rectangle getIllustrationRectangle(int row, int col) {
            int x = xBounds[col][0];
            int y = yBounds[row][0];
            int width = xBounds[col][1] - xBounds[col][0];
            int height = yBounds[row][1] - yBounds[row][0];
            return new Rectangle(x, y, width, height);
        }
    }
    
    /**
     * Detect grid bounds for 2x2 grid with transparent boundaries
     */
    private GridBounds detectGridBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        log.debug("Detecting intelligent grid bounds for 2x2 illustration grid: {}x{}", width, height);
        
        // Try to detect transparent boundaries first
        int[] xBoundaries = detectTransparentBoundaries(image, true);
        int[] yBoundaries = detectTransparentBoundaries(image, false);
        
        // Fall back to simple division if transparent boundaries not found
        if (xBoundaries == null) {
            xBoundaries = new int[]{0, width / 2, width};
            log.debug("Using simple division for x-axis: {}", java.util.Arrays.toString(xBoundaries));
        }
        
        if (yBoundaries == null) {
            yBoundaries = new int[]{0, height / 2, height};
            log.debug("Using simple division for y-axis: {}", java.util.Arrays.toString(yBoundaries));
        }
        
        // Convert boundaries to GridBounds format
        int[][] xBounds = new int[2][2]; // [column][start/end]
        int[][] yBounds = new int[2][2]; // [row][start/end]
        
        for (int i = 0; i < 2; i++) {
            xBounds[i][0] = xBoundaries[i];
            xBounds[i][1] = xBoundaries[i + 1];
            yBounds[i][0] = yBoundaries[i];
            yBounds[i][1] = yBoundaries[i + 1];
        }
        
        log.debug("Grid bounds detected - Columns: [{},{}] and [{},{}], Rows: [{},{}] and [{},{}]",
            xBounds[0][0], xBounds[0][1], xBounds[1][0], xBounds[1][1],
            yBounds[0][0], yBounds[0][1], yBounds[1][0], yBounds[1][1]);
        
        return new GridBounds(xBounds, yBounds);
    }
    
    /**
     * Detect grid boundaries by looking for transparent pixels (for 2x2 grid)
     */
    private int[] detectTransparentBoundaries(BufferedImage image, boolean vertical) {
        int dimension = vertical ? image.getWidth() : image.getHeight();
        
        log.debug("Detecting transparent {} boundaries for 2x2 grid, dimension: {}",
            vertical ? "vertical" : "horizontal", dimension);
        
        // For 2x2 grid, we only need to find one boundary (at the middle)
        int idealBoundary = dimension / 2;
        int searchRange = dimension / 10; // Search within 10% of center
        
        // Find best transparent line near the ideal center position
        int bestBoundary = findBestTransparentLine(image, vertical,
            Math.max(1, idealBoundary - searchRange),
            Math.min(dimension - 1, idealBoundary + searchRange));
        
        if (bestBoundary == -1) {
            log.debug("Could not find transparent {} boundary, using simple division", 
                vertical ? "vertical" : "horizontal");
            return null;
        }
        
        log.debug("Found transparent {} boundary at position {}", 
            vertical ? "vertical" : "horizontal", bestBoundary);
        
        return new int[]{0, bestBoundary, dimension};
    }
    
    /**
     * Find the line with the highest transparency percentage within a given range
     */
    private int findBestTransparentLine(BufferedImage image, boolean vertical, int startPos, int endPos) {
        int bestPosition = -1;
        double bestTransparency = 0.0;
        double minTransparency = 0.90; // Minimum 90% transparency
        
        for (int pos = startPos; pos <= endPos; pos++) {
            double transparency = calculateLineTransparency(image, vertical, pos);
            
            if (transparency >= minTransparency && transparency > bestTransparency) {
                bestTransparency = transparency;
                bestPosition = pos;
            }
        }
        
        if (bestPosition != -1) {
            log.debug("Best transparent line at position {} with {}% transparency",
                bestPosition, String.format("%.1f", bestTransparency * 100));
        }
        
        return bestPosition;
    }
    
    /**
     * Calculate the percentage of transparent pixels along a line
     */
    private double calculateLineTransparency(BufferedImage image, boolean vertical, int position) {
        int dimension = vertical ? image.getHeight() : image.getWidth();
        int transparentPixels = 0;
        int totalPixels = dimension;
        
        for (int i = 0; i < dimension; i++) {
            int rgb = vertical ? image.getRGB(position, i) : image.getRGB(i, position);
            int alpha = (rgb >> 24) & 0xFF;
            
            if (alpha < 20) { // Very low alpha
                transparentPixels++;
            }
        }
        
        return (double) transparentPixels / totalPixels;
    }
    
    /**
     * Detect and remove solid frame artifacts
     */
    private BufferedImage detectAndRemoveSolidFrame(BufferedImage image) {
        if (image == null || image.getWidth() < 50 || image.getHeight() < 50) {
            return image;
        }
        
        // Simple frame detection - check if edges are solid
        int topFrameThickness = detectFrameThickness(image, "top");
        int bottomFrameThickness = detectFrameThickness(image, "bottom");
        int leftFrameThickness = detectFrameThickness(image, "left");
        int rightFrameThickness = detectFrameThickness(image, "right");
        
        int maxFrameThickness = Math.max(Math.max(topFrameThickness, bottomFrameThickness),
            Math.max(leftFrameThickness, rightFrameThickness));
        
        if (maxFrameThickness == 0 || maxFrameThickness > 5) {
            return image;
        }
        
        log.info("Removing solid frame of thickness {} from illustration", maxFrameThickness);
        
        try {
            int newWidth = image.getWidth() - (2 * maxFrameThickness);
            int newHeight = image.getHeight() - (2 * maxFrameThickness);
            
            if (newWidth > 10 && newHeight > 10) {
                return image.getSubimage(maxFrameThickness, maxFrameThickness, newWidth, newHeight);
            }
        } catch (Exception e) {
            log.error("Error cropping solid frame", e);
        }
        
        return image;
    }
    
    /**
     * Detect frame thickness on a specific edge
     */
    private int detectFrameThickness(BufferedImage image, String edge) {
        int width = image.getWidth();
        int height = image.getHeight();
        int maxThickness = 5;
        
        for (int thickness = 1; thickness <= maxThickness; thickness++) {
            int solidPixelCount = 0;
            int sampleCount = 0;
            
            switch (edge.toLowerCase()) {
                case "top":
                    if (thickness >= height) break;
                    for (int x = 0; x < width; x += Math.max(1, width / 50)) {
                        int rgb = image.getRGB(x, thickness - 1);
                        int alpha = (rgb >> 24) & 0xFF;
                        sampleCount++;
                        if (alpha > 10) solidPixelCount++;
                    }
                    break;
                    
                case "bottom":
                    if (thickness >= height) break;
                    for (int x = 0; x < width; x += Math.max(1, width / 50)) {
                        int rgb = image.getRGB(x, height - thickness);
                        int alpha = (rgb >> 24) & 0xFF;
                        sampleCount++;
                        if (alpha > 10) solidPixelCount++;
                    }
                    break;
                    
                case "left":
                    if (thickness >= width) break;
                    for (int y = 0; y < height; y += Math.max(1, height / 50)) {
                        int rgb = image.getRGB(thickness - 1, y);
                        int alpha = (rgb >> 24) & 0xFF;
                        sampleCount++;
                        if (alpha > 10) solidPixelCount++;
                    }
                    break;
                    
                case "right":
                    if (thickness >= width) break;
                    for (int y = 0; y < height; y += Math.max(1, height / 50)) {
                        int rgb = image.getRGB(width - thickness, y);
                        int alpha = (rgb >> 24) & 0xFF;
                        sampleCount++;
                        if (alpha > 10) solidPixelCount++;
                    }
                    break;
                    
                default:
                    return 0;
            }
            
            double solidRatio = sampleCount > 0 ? (double) solidPixelCount / sampleCount : 0.0;
            
            if (solidRatio < 0.8) {
                return thickness - 1;
            }
        }
        
        return maxThickness;
    }
    
    /**
     * Center an illustration by detecting its content bounding box and placing it centered on a rectangular canvas
     */
    public BufferedImage centerIllustration(BufferedImage illustrationImage, int targetWidth, int targetHeight) {
        if (illustrationImage == null) {
            throw new IllegalArgumentException("Illustration image cannot be null");
        }
        
        log.debug("Centering illustration of size {}x{}", illustrationImage.getWidth(), illustrationImage.getHeight());
        
        // Detect content bounds
        Rectangle contentBounds = detectContentBounds(illustrationImage);
        
        if (contentBounds == null || contentBounds.width == 0 || contentBounds.height == 0) {
            log.warn("No content detected in illustration, returning original image");
            return illustrationImage;
        }
        
        // Crop to content bounding box
        BufferedImage croppedContent = illustrationImage.getSubimage(
            contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);
        
        // Create centered canvas with rectangular dimensions
        BufferedImage centeredIllustration = createCenteredCanvas(croppedContent, targetWidth, targetHeight);
        
        log.debug("Successfully centered illustration to {}x{} canvas", targetWidth, targetHeight);
        return centeredIllustration;
    }
    
    /**
     * Detect the bounding box of all non-background pixels
     */
    private Rectangle detectContentBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        
        boolean hasContent = false;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                
                boolean isTransparent = alpha < 10;
                boolean isWhite = red > 245 && green > 245 && blue > 245;
                
                if (!isTransparent && !isWhite) {
                    hasContent = true;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        
        if (!hasContent) {
            log.warn("No content detected in illustration image");
            return null;
        }
        
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
    
    /**
     * Create a new rectangular canvas and center the given image on it
     */
    private BufferedImage createCenteredCanvas(BufferedImage contentImage, int canvasWidth, int canvasHeight) {
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = canvas.createGraphics();
        
        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Fill with transparent background
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, canvasWidth, canvasHeight);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // Calculate scaling and positioning
        int contentWidth = contentImage.getWidth();
        int contentHeight = contentImage.getHeight();
        
        double maxContentWidth = canvasWidth * 0.9; // Use 90% of canvas width
        double maxContentHeight = canvasHeight * 0.9; // Use 90% of canvas height
        double scale = 1.0;
        
        if (contentWidth > maxContentWidth || contentHeight > maxContentHeight) {
            double scaleX = maxContentWidth / contentWidth;
            double scaleY = maxContentHeight / contentHeight;
            scale = Math.min(scaleX, scaleY);
        }
        
        int scaledWidth = (int) (contentWidth * scale);
        int scaledHeight = (int) (contentHeight * scale);
        
        int x = (canvasWidth - scaledWidth) / 2;
        int y = (canvasHeight - scaledHeight) / 2;
        
        g2d.drawImage(contentImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return canvas;
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

