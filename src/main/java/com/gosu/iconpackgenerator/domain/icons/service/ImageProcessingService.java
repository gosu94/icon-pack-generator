package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.singal.SignalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingService {

    private final SignalMessageService signalMessageService;

    /**
     * Helper class to track processing timing for performance analysis
     */
    private static class ProcessingTiming {
        public long transparencyCheckMs = 0;
        public long backgroundRemovalMs = 0;
        public long solidFrameDetectionMs = 0;
        public long diagnosticsMs = 0;
        public long gridBoundsDetectionMs = 0;
        public long artifactCleanupMs = 0;
        public long iconCenteringMs = 0;
        
        public long getTotalMs() {
            return transparencyCheckMs + backgroundRemovalMs + solidFrameDetectionMs + 
                   diagnosticsMs + gridBoundsDetectionMs + artifactCleanupMs + iconCenteringMs;
        }
    }


    public static final int ICON_TARGET_SIZE = 300;
    private final BackgroundRemovalService backgroundRemovalService;
    private final IconCenteringService iconCenteringService;
    private final GridBoundaryDetectionService gridBoundaryDetectionService;
    private final IconArtifactCleanupService iconArtifactCleanupService;

    /**
     * Crop a 3x3 grid of icons from the generated image
     *
     * @param imageData The original image as byte array
     * @param iconCount The number of icons to extract (9 or 18)
     * @return List of cropped icon images as base64 strings
     */
    public List<String> cropIconsFromGrid(byte[] imageData, int iconCount, boolean removeBackground) {
        return cropIconsFromGrid(imageData, iconCount, true, ICON_TARGET_SIZE, removeBackground, true);
    }

    /**
     * Crop a 3x3 grid of icons from the generated image with optional centering
     *
     * @param imageData   The original image as byte array
     * @param iconCount   The number of icons to extract (9 or 18)
     * @param centerIcons Whether to center the icons on their canvas
     * @param targetSize  Target size for centered icons (0 = auto-size based on original)
     * @return List of cropped icon images as base64 strings
     */
    public List<String> cropIconsFromGrid(byte[] imageData, int iconCount, boolean centerIcons, int targetSize) {
        return cropIconsFromGrid(imageData, iconCount, centerIcons, targetSize, false, true);
    }

    /**
     * Crop a 3x3 grid of icons from the generated image with optional centering and background removal
     *
     * @param imageData        The original image as byte array
     * @param iconCount        The number of icons to extract (9 or 18)
     * @param centerIcons      Whether to center the icons on their canvas
     * @param targetSize       Target size for centered icons (0 = auto-size based on original)
     * @param removeBackground Whether to remove background from the entire grid before cropping (typically false during generation, true during export)
     * @param cleanupArtifacts Whether to clean up artifacts from neighboring icons by making them transparent
     * @return List of cropped icon images as base64 strings
     */
    public List<String> cropIconsFromGrid(byte[] imageData, int iconCount, boolean centerIcons, int targetSize, boolean removeBackground, boolean cleanupArtifacts) {
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

            // Performance timing tracking
            long totalStartTime = System.currentTimeMillis();
            ProcessingTiming timing = new ProcessingTiming();

            // Optionally remove background from the entire grid image before processing
            byte[] processedImageData = imageData;
            if (removeBackground) {
                try {
                    // Heuristic check to see if background is already transparent
                    long transparencyCheckStart = System.currentTimeMillis();
                    BufferedImage imageToCheck = ImageIO.read(new ByteArrayInputStream(imageData));
                    boolean hasTransparentBg = imageToCheck != null && hasTransparentBackground(imageToCheck);
                    timing.transparencyCheckMs = System.currentTimeMillis() - transparencyCheckStart;
                    
                    if (hasTransparentBg) {
                        log.info("Image background appears to be already transparent. Skipping background removal.");
                    } else {
                        if (imageToCheck == null) {
                            log.warn("Could not read image to check for transparency, proceeding with background removal.");
                        }
                        log.info("Removing background from grid image before cropping icons");
                        signalMessageService.sendSignalMessage("[IconPackGen] Background removal detected");

                        // Add timeout protection for background removal
                        long backgroundRemovalStart = System.currentTimeMillis();
                        processedImageData = backgroundRemovalService.removeBackground(imageData);
                        timing.backgroundRemovalMs = System.currentTimeMillis() - backgroundRemovalStart;

                        log.info("Background removal completed in {} ms", timing.backgroundRemovalMs);

                        if (processedImageData.length != imageData.length) {
                            log.info("Background removal changed image size from {} to {} bytes",
                                    imageData.length, processedImageData.length);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error during transparency check, proceeding with background removal.", e);
                    long backgroundRemovalStart = System.currentTimeMillis();
                    processedImageData = backgroundRemovalService.removeBackground(imageData);
                    timing.backgroundRemovalMs = System.currentTimeMillis() - backgroundRemovalStart;
                }
            } else {
                log.debug("Background removal disabled - preserving original image for better content bounds detection");
            }

            BufferedImage originalImage = null;
            //OPENAI models do not return webp pictures
//            // Check if it's WebP format for better logging
//            if (isWebPFormat(processedImageData)) {
//                log.info("WebP format detected. Attempting to decode using TwelveMonkeys ImageIO WebP support...");
//                originalImage = readImageWithWebPSupport(processedImageData);
//            } else {

            log.debug("Standard image format detected, using regular ImageIO...");
            originalImage = ImageIO.read(new ByteArrayInputStream(processedImageData));

            if (originalImage == null) {
                log.error("Failed to parse image data - ImageIO.read() returned null. Data size: {} bytes", processedImageData.length);
                // Log first few bytes to help debug
                if (processedImageData.length > 10) {
                    byte[] firstBytes = java.util.Arrays.copyOf(processedImageData, 10);
                    log.error("First 10 bytes of image data: {}", java.util.Arrays.toString(firstBytes));
                }
                throw new RuntimeException("Failed to parse image data - ImageIO returned null");
            }

            log.debug("Successfully parsed image: {}x{} pixels", originalImage.getWidth(), originalImage.getHeight());

            // Check for and remove solid frame artifacts (common in image-to-image generation)
            long frameDetectionStart = System.currentTimeMillis();
            BufferedImage frameCleanedImage = detectAndRemoveSolidFrame(originalImage);
            timing.solidFrameDetectionMs = System.currentTimeMillis() - frameDetectionStart;
            
            if (frameCleanedImage != originalImage) {
                log.info("Detected and removed solid frame from image. New dimensions: {}x{}", 
                        frameCleanedImage.getWidth(), frameCleanedImage.getHeight());
                originalImage = frameCleanedImage;
            }

            // Add image diagnostics
            long diagnosticsStart = System.currentTimeMillis();
            performImageDiagnostics(originalImage, processedImageData.length);
            timing.diagnosticsMs = System.currentTimeMillis() - diagnosticsStart;

            List<String> croppedIcons = new ArrayList<>();

            if (iconCount == 9) {
                croppedIcons.addAll(cropGrid3x3(originalImage, centerIcons, targetSize, cleanupArtifacts, timing));
            } else if (iconCount == 18) {
                croppedIcons.addAll(cropGrid3x3(originalImage, centerIcons, targetSize, cleanupArtifacts, timing));
                // TODO: Handle second grid generation for 18 icons
            }

            // Log comprehensive timing summary
            long totalProcessingTime = System.currentTimeMillis() - totalStartTime;
            logProcessingTimingSummary(timing, totalProcessingTime, iconCount, originalImage.getWidth(), originalImage.getHeight());

            log.debug("Successfully cropped {} icons from grid", croppedIcons.size());
            return croppedIcons;

        } catch (IOException e) {
            log.error("Error processing image", e);
            throw new RuntimeException("Failed to process image", e);
        }
    }

    /**
     * Heuristic check to see if an image's background is already transparent.
     * It samples pixels along the border of the image.
     *
     * @param image The image to check.
     * @return true if the background is likely transparent, false otherwise.
     */
    private boolean hasTransparentBackground(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Don't check tiny images
        if (width < 50 || height < 50) {
            return false;
        }

        int sampleStep = Math.max(1, Math.min(width, height) / 20); // Sample ~20 pixels per edge
        int transparentThreshold = 10; // Alpha value below this is considered transparent
        int nonTransparentCount = 0;
        int samples = 0;

        // Sample top and bottom edges
        for (int x = 0; x < width; x += sampleStep) {
            if (((image.getRGB(x, 0) >> 24) & 0xff) > transparentThreshold) nonTransparentCount++;
            if (((image.getRGB(x, height - 1) >> 24) & 0xff) > transparentThreshold) nonTransparentCount++;
            samples += 2;
        }

        // Sample left and right edges
        for (int y = 1; y < height - 1; y += sampleStep) {
            if (((image.getRGB(0, y) >> 24) & 0xff) > transparentThreshold) nonTransparentCount++;
            if (((image.getRGB(width - 1, y) >> 24) & 0xff) > transparentThreshold) nonTransparentCount++;
            samples += 2;
        }

        // If more than 10% of sampled border pixels are not transparent, assume there's a background.
        double nonTransparentRatio = (double) nonTransparentCount / samples;
        log.info("Transparency check: {} non-transparent pixels out of {} samples on the border (ratio: {}).", nonTransparentCount, samples, String.format("%.2f", nonTransparentRatio));

        return nonTransparentRatio < 0.10;
    }

    /**
     * Detect and remove thin solid frames that sometimes appear around generated images.
     * These frames (typically 1-3px thick) can be any color and interfere with proper grid detection and centering.
     *
     * @param image The image to analyze and potentially clean
     * @return The original image if no frame detected, or a cropped image with the frame removed
     */
    private BufferedImage detectAndRemoveSolidFrame(BufferedImage image) {
        if (image == null) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // Don't process very small images
        if (width < 50 || height < 50) {
            log.debug("Image too small for solid frame detection: {}x{}", width, height);
            return image;
        }

        log.debug("Analyzing image for solid frame artifacts: {}x{}", width, height);

        // Detect frame thickness from each edge
        int topFrameThickness = detectFrameThickness(image, "top");
        int bottomFrameThickness = detectFrameThickness(image, "bottom");
        int leftFrameThickness = detectFrameThickness(image, "left");
        int rightFrameThickness = detectFrameThickness(image, "right");

        // Check if we have a consistent frame on all edges
        int maxFrameThickness = Math.max(Math.max(topFrameThickness, bottomFrameThickness),
                Math.max(leftFrameThickness, rightFrameThickness));

        if (maxFrameThickness == 0) {
            log.debug("No solid frame detected");
            return image;
        }

        // Validate that the frame is reasonably consistent and not too thick
        int minFrameThickness = Math.min(Math.min(topFrameThickness, bottomFrameThickness),
                Math.min(leftFrameThickness, rightFrameThickness));

        // Frame should be present on most edges and be reasonably thin (1-5 pixels max)
        if (maxFrameThickness > 5 || (maxFrameThickness - minFrameThickness) > 2) {
            log.debug("Inconsistent or too thick frame detected: top={}, bottom={}, left={}, right={} - skipping removal",
                    topFrameThickness, bottomFrameThickness, leftFrameThickness, rightFrameThickness);
            return image;
        }

        // Use the most conservative (smallest) frame thickness for cropping
        int cropAmount = minFrameThickness;
        if (cropAmount == 0) {
            cropAmount = 1; // Remove at least 1px if we detected any frame
        }

        log.info("Detected solid frame with thickness: top={}, bottom={}, left={}, right={} - removing {}px from each edge",
                topFrameThickness, bottomFrameThickness, leftFrameThickness, rightFrameThickness, cropAmount);

        // Crop the frame
        int newX = cropAmount;
        int newY = cropAmount;
        int newWidth = width - (2 * cropAmount);
        int newHeight = height - (2 * cropAmount);

        // Ensure we don't crop too much
        if (newWidth <= 10 || newHeight <= 10) {
            log.warn("Solid frame crop would make image too small ({}x{} -> {}x{}) - skipping removal",
                    width, height, newWidth, newHeight);
            return image;
        }

        try {
            BufferedImage croppedImage = image.getSubimage(newX, newY, newWidth, newHeight);
            log.info("Successfully removed solid frame. Image dimensions: {}x{} -> {}x{}",
                    width, height, newWidth, newHeight);
            return croppedImage;
        } catch (Exception e) {
            log.error("Error cropping solid frame", e);
            return image; // Return original on error
        }
    }

    /**
     * Detect the thickness of a solid frame on a specific edge of the image
     *
     * @param image The image to analyze
     * @param edge  The edge to analyze: "top", "bottom", "left", or "right"
     * @return The thickness of the solid frame in pixels, or 0 if no frame detected
     */
    private int detectFrameThickness(BufferedImage image, String edge) {
        int width = image.getWidth();
        int height = image.getHeight();
        int maxThickness = Math.min(5, Math.min(width, height) / 10); // Max 5px or 10% of smallest dimension

        for (int thickness = 1; thickness <= maxThickness; thickness++) {
            int sampleCount = 0;
            int solidPixelCount = 0;

            // Sample pixels along the edge at the given thickness
            switch (edge.toLowerCase()) {
                case "top":
                    if (thickness >= height) break;
                    for (int x = 0; x < width; x += Math.max(1, width / 50)) { // Sample ~50 points
                        int rgb = image.getRGB(x, thickness - 1); // 0-indexed
                        sampleCount++;
                        if (isSolidFramePixel(rgb)) {
                            solidPixelCount++;
                        }
                    }
                    break;

                case "bottom":
                    if (thickness >= height) break;
                    for (int x = 0; x < width; x += Math.max(1, width / 50)) {
                        int rgb = image.getRGB(x, height - thickness);
                        sampleCount++;
                        if (isSolidFramePixel(rgb)) {
                            solidPixelCount++;
                        }
                    }
                    break;

                case "left":
                    if (thickness >= width) break;
                    for (int y = 0; y < height; y += Math.max(1, height / 50)) {
                        int rgb = image.getRGB(thickness - 1, y); // 0-indexed
                        sampleCount++;
                        if (isSolidFramePixel(rgb)) {
                            solidPixelCount++;
                        }
                    }
                    break;

                case "right":
                    if (thickness >= width) break;
                    for (int y = 0; y < height; y += Math.max(1, height / 50)) {
                        int rgb = image.getRGB(width - thickness, y);
                        sampleCount++;
                        if (isSolidFramePixel(rgb)) {
                            solidPixelCount++;
                        }
                    }
                    break;

                default:
                    return 0;
            }

            // Check if this line is predominantly solid (non-transparent)
            double solidRatio = sampleCount > 0 ? (double) solidPixelCount / sampleCount : 0.0;
            
            if (solidRatio >= 0.8) { // 80% of pixels should be solid (non-transparent)
                log.debug("Detected solid frame on {} edge at thickness {}: {}/{} samples are solid ({}%)",
                        edge, thickness, solidPixelCount, sampleCount, String.format("%.1f", solidRatio * 100));
                continue; // This thickness level is part of the frame
            } else {
                // This thickness level is not predominantly solid, so frame ends at previous thickness
                int frameThickness = thickness - 1;
                if (frameThickness > 0) {
                    log.debug("Solid frame on {} edge detected with thickness: {} pixels", edge, frameThickness);
                }
                return frameThickness;
            }
        }

        // If we've checked all thickness levels and they're all solid, return the max
        log.debug("Solid frame on {} edge extends to maximum checked thickness: {} pixels", edge, maxThickness);
        return maxThickness;
    }

    /**
     * Check if a pixel is solid (non-transparent) and could be part of a frame
     * This method detects any solid color that could form a frame, regardless of the actual color
     *
     * @param rgb The RGB value of the pixel
     * @return true if the pixel appears to be part of a solid frame
     */
    private boolean isSolidFramePixel(int rgb) {
        int alpha = (rgb >> 24) & 0xFF;
        
        // Consider pixels with low to moderate opacity as potentially solid frame pixels
        // Use a lower threshold to catch subtle frames that might be very light gray or semi-transparent
        // Alpha > 10 catches anything that's not almost completely transparent
        if (alpha <= 10) {
            return false; // Skip nearly transparent pixels
        }
        
        // For subtle frame detection, also consider very light colors that are not pure white/transparent
        // This helps catch light grey frames that might have been generated by AI models
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        
        // Skip pure white pixels (they're typically background)
        boolean isPureWhite = red >= 250 && green >= 250 && blue >= 250;
        
        return !isPureWhite;
    }

    /**
     * Center an icon by detecting its content bounding box and placing it centered on a square canvas
     *
     * @param iconImage  The original icon image
     * @param targetSize The size of the square canvas (if 0, uses the original image's larger dimension)
     * @return The centered icon as a BufferedImage
     */
    public BufferedImage centerIcon(BufferedImage iconImage, int targetSize) {
        return iconCenteringService.centerIcon(iconImage, targetSize);
    }

    /**
     * Center an icon and return it as a base64 string
     *
     * @param iconImage  The original icon image
     * @param targetSize The size of the square canvas (if 0, uses the original image's larger dimension)
     * @return The centered icon as a base64 string
     */
    public String centerIconToBase64(BufferedImage iconImage, int targetSize) throws IOException {
        BufferedImage centeredIcon = centerIcon(iconImage, targetSize);
        return bufferedImageToBase64(centeredIcon);
    }

    /**
     * Remove background from an individual icon's base64 data
     *
     * @param base64IconData The icon as base64 string
     * @return The icon with background removed as base64 string
     */
    public String removeBackgroundFromIcon(String base64IconData) {
        try {
            // Decode base64 to byte array
            byte[] iconBytes = Base64.getDecoder().decode(base64IconData);

            // Remove background
            byte[] processedBytes = backgroundRemovalService.removeBackground(iconBytes);

            // Encode back to base64
            return Base64.getEncoder().encodeToString(processedBytes);

        } catch (Exception e) {
            log.error("Error removing background from individual icon", e);
            // Return original icon if background removal fails
            return base64IconData;
        }
    }

    /**
     * Perform diagnostic analysis on the image to help debug processing issues
     */
    private void performImageDiagnostics(BufferedImage image, int originalFileSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Sample some pixels to understand the image content
        int sampleSize = Math.min(100, width * height / 10); // Sample up to 100 pixels or 10% of image
        int transparentCount = 0;
        int whiteCount = 0;
        int blackCount = 0;
        int colorCount = 0;

        java.util.Random random = new java.util.Random();

        for (int i = 0; i < sampleSize; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            int rgb = image.getRGB(x, y);

            int alpha = (rgb >> 24) & 0xFF;
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;

            if (alpha < 10) {
                transparentCount++;
            } else if (red > 245 && green > 245 && blue > 245) {
                whiteCount++;
            } else if (red < 10 && green < 10 && blue < 10) {
                blackCount++;
            } else {
                colorCount++;
            }
        }

        // Log diagnostic information
        log.debug("Image diagnostics: {}x{} pixels, {} bytes original file size", width, height, originalFileSize);
        log.debug("Pixel sample analysis ({}): {}% transparent, {}% white, {}% black, {}% colored",
                sampleSize,
                Math.round(100.0 * transparentCount / sampleSize),
                Math.round(100.0 * whiteCount / sampleSize),
                Math.round(100.0 * blackCount / sampleSize),
                Math.round(100.0 * colorCount / sampleSize));

        // Warn about potential issues
        if (blackCount > sampleSize * 0.8) {
            log.warn("Image appears to be mostly black - this might be a generation error or failed image download");
        }

        if (transparentCount > sampleSize * 0.9) {
            log.warn("Image is mostly transparent - this might indicate a generation issue");
        }

        if (originalFileSize < 5000) {
            log.warn("Image file size is very small ({} bytes) - this might indicate a failed generation", originalFileSize);
        }

    }

    private List<String> cropGrid3x3(BufferedImage originalImage, boolean centerIcons, int targetSize, boolean cleanupArtifacts, ProcessingTiming timing) throws IOException {
        List<String> icons = new ArrayList<>();

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        log.debug("Starting intelligent 3x3 grid cropping for image {}x{}", width, height);

        long gridBoundsStart = System.currentTimeMillis();
        GridBoundaryDetectionService.GridBounds gridBounds = gridBoundaryDetectionService.detectGridBounds(originalImage);
        timing.gridBoundsDetectionMs = System.currentTimeMillis() - gridBoundsStart;

        // Determine if we should skip artifact cleanup due to perfect transparency
        boolean shouldSkipCleanup = gridBounds.hasPerfectTransparency();
        if (cleanupArtifacts && shouldSkipCleanup) {
            log.debug("Skipping artifact cleanup - perfect transparent grid lines detected");
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                Rectangle iconRect = gridBounds.getIconRectangle(row, col);

                log.debug("Cropping icon at position [{},{}] with bounds: x={}, y={}, width={}, height={}",
                        row, col, iconRect.x, iconRect.y, iconRect.width, iconRect.height);

                BufferedImage croppedIcon = originalImage.getSubimage(
                        iconRect.x, iconRect.y, iconRect.width, iconRect.height);

                // Optionally cleanup artifacts from neighboring icons
                // Skip cleanup if we have perfect transparent grid lines (no artifacts expected)
                long artifactCleanupStart = System.currentTimeMillis();
                if (cleanupArtifacts && !shouldSkipCleanup) {
                    croppedIcon = iconArtifactCleanupService.cleanupIconArtifacts(croppedIcon, row, col);
                    log.debug("Cleaned up artifacts for icon at position [{},{}]", row, col);
                }
                timing.artifactCleanupMs += System.currentTimeMillis() - artifactCleanupStart;

                // Optionally center the icon
                long centeringStart = System.currentTimeMillis();
                if (centerIcons) {
                    int size = targetSize > 0 ? targetSize : Math.max(iconRect.width, iconRect.height);
                    croppedIcon = centerIcon(croppedIcon, size);
                    log.debug("Centered icon at position [{},{}] to size {}x{}", row, col, size, size);
                } else {
                    log.debug("Cropped icon at position [{},{}] with size {}x{}",
                            row, col, iconRect.width, iconRect.height);
                }
                timing.iconCenteringMs += System.currentTimeMillis() - centeringStart;

                String base64Icon = bufferedImageToBase64(croppedIcon);
                icons.add(base64Icon);
            }
        }

        return icons;
    }

    /**
     * Log a comprehensive summary of processing timing for performance analysis
     */
    private void logProcessingTimingSummary(ProcessingTiming timing, long totalProcessingTime, int iconCount, int imageWidth, int imageHeight) {
        log.info("🚀 IMAGE PROCESSING PERFORMANCE SUMMARY 🚀");
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ Image: {}x{} pixels, Processing {} icons", imageWidth, imageHeight, iconCount);
        log.info("├─────────────────────────────────────────────────────────────");
        log.info("│ ⏱️  TIMING BREAKDOWN:");
        log.info(String.format("│   • Transparency Check:     %6d ms  (%5.1f%%)", 
                timing.transparencyCheckMs, getPercentage(timing.transparencyCheckMs, totalProcessingTime)));
        log.info(String.format("│   • Background Removal:     %6d ms  (%5.1f%%)", 
                timing.backgroundRemovalMs, getPercentage(timing.backgroundRemovalMs, totalProcessingTime)));
        log.info(String.format("│   • Solid Frame Detection:  %6d ms  (%5.1f%%)", 
                timing.solidFrameDetectionMs, getPercentage(timing.solidFrameDetectionMs, totalProcessingTime)));
        log.info(String.format("│   • Image Diagnostics:      %6d ms  (%5.1f%%)", 
                timing.diagnosticsMs, getPercentage(timing.diagnosticsMs, totalProcessingTime)));
        log.info(String.format("│   • Grid Bounds Detection:  %6d ms  (%5.1f%%)", 
                timing.gridBoundsDetectionMs, getPercentage(timing.gridBoundsDetectionMs, totalProcessingTime)));
        log.info(String.format("│   • Artifact Cleanup:       %6d ms  (%5.1f%%)", 
                timing.artifactCleanupMs, getPercentage(timing.artifactCleanupMs, totalProcessingTime)));
        log.info(String.format("│   • Icon Centering:         %6d ms  (%5.1f%%)", 
                timing.iconCenteringMs, getPercentage(timing.iconCenteringMs, totalProcessingTime)));
        log.info("├─────────────────────────────────────────────────────────────");
        log.info(String.format("│   🎯 MEASURED TOTAL:         %6d ms  (%5.1f%%)", 
                timing.getTotalMs(), getPercentage(timing.getTotalMs(), totalProcessingTime)));
        log.info(String.format("│   ⚡ ACTUAL TOTAL:           %6d ms  (100.0%%)", totalProcessingTime));
        log.info(String.format("│   📊 OVERHEAD:               %6d ms  (%5.1f%%)", 
                totalProcessingTime - timing.getTotalMs(), 
                getPercentage(totalProcessingTime - timing.getTotalMs(), totalProcessingTime)));
        log.info("├─────────────────────────────────────────────────────────────");
        log.info("│ 📈 PERFORMANCE METRICS:");
        log.info(String.format("│   • Processing rate:        %6.1f ms/icon", (double)totalProcessingTime / iconCount));
        log.info(String.format("│   • Pixels processed:       %6d MP/s", 
                Math.round((imageWidth * imageHeight / 1_000_000.0) / (totalProcessingTime / 1000.0))));
        log.info("└─────────────────────────────────────────────────────────────");
    }

    /**
     * Calculate percentage for timing display
     */
    private double getPercentage(long value, long total) {
        return total > 0 ? (double) value * 100.0 / total : 0.0;
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
                log.debug("Successfully read WebP image: {}x{} pixels", image.getWidth(), image.getHeight());
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
