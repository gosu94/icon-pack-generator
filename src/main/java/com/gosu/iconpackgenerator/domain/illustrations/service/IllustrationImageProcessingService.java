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
    private static final ThreadLocal<Long> upscaleTimeHolder = new ThreadLocal<>();

    public static final int ILLUSTRATION_TARGET_SIZE = 600; // Larger than icons

    /**
     * Helper class to track processing timing for performance analysis
     */
    private static class ProcessingTiming {
        public long backgroundRemovalMs = 0;
        public long imageParsingMs = 0;
        public long solidFrameDetectionMs = 0;
        public long gridBoundsDetectionMs = 0;
        public long contentBoundsDetectionMs = 0;
        public long illustrationResizingMs = 0;
        public long upscaleMs = 0;

        public long getTotalMs() {
            return backgroundRemovalMs + imageParsingMs + solidFrameDetectionMs +
                    gridBoundsDetectionMs + contentBoundsDetectionMs + illustrationResizingMs + upscaleMs;
        }
    }

    public List<String> cropIllustrationsFromGrid(byte[] imageData, long upscaleTime) {
        upscaleTimeHolder.set(upscaleTime);
        try {
            return cropIllustrationsFromGrid(imageData);
        } finally {
            upscaleTimeHolder.remove();
        }
    }

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
     * @param imageData           The original image as byte array
     * @param centerIllustrations Whether to center the illustrations on their canvas
     * @param targetSize          Target size for centered illustrations
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
            ProcessingTiming timing = new ProcessingTiming();

            Long upscaleTime = upscaleTimeHolder.get();
            if (upscaleTime != null) {
                timing.upscaleMs = upscaleTime;
            }

            // Background removal - currently we dont need it
//            long backgroundRemovalStart = System.currentTimeMillis();
//            log.info("Removing background from illustration grid image (mandatory step)");
//            byte[] processedImageData = backgroundRemovalService.removeBackground(imageData);
//            timing.backgroundRemovalMs = System.currentTimeMillis() - backgroundRemovalStart;
//            log.info("Background removal completed in {} ms", timing.backgroundRemovalMs);
            timing.backgroundRemovalMs = 0;

            // Image parsing
            long imageParsingStart = System.currentTimeMillis();
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            timing.imageParsingMs = System.currentTimeMillis() - imageParsingStart;

            if (originalImage == null) {
                log.error("Failed to parse image data - ImageIO.read() returned null");
                throw new RuntimeException("Failed to parse image data");
            }

            log.info("Successfully parsed illustration image: {}x{} pixels in {} ms",
                    originalImage.getWidth(), originalImage.getHeight(), timing.imageParsingMs);

            // Detect and remove solid frame artifacts
            long frameDetectionStart = System.currentTimeMillis();
            BufferedImage frameCleanedImage = detectAndRemoveSolidFrame(originalImage);
            timing.solidFrameDetectionMs = System.currentTimeMillis() - frameDetectionStart;
            if (frameCleanedImage != originalImage) {
                log.info("Detected and removed solid frame from illustration image in {} ms", timing.solidFrameDetectionMs);
                originalImage = frameCleanedImage;
            }

            List<String> croppedIllustrations = crop2x2Grid(originalImage, centerIllustrations, targetSize, timing);

            long totalProcessingTime = System.currentTimeMillis() - totalStartTime;
            logProcessingTimingSummary(timing, totalProcessingTime, 4, originalImage.getWidth(), originalImage.getHeight());
            log.info("Successfully cropped {} illustrations in {}ms", croppedIllustrations.size(), totalProcessingTime);

            return croppedIllustrations;

        } catch (IOException e) {
            log.error("Error processing illustration image", e);
            throw new RuntimeException("Failed to process illustration image", e);
        }
    }

    /**
     * Crop a 2x2 grid of illustrations
     * All illustrations will have the same size with no transparent borders
     */
    private List<String> crop2x2Grid(BufferedImage originalImage, boolean centerIllustrations, int targetSize, ProcessingTiming timing) throws IOException {
        List<String> illustrations = new ArrayList<>();

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        log.info("Starting intelligent 2x2 grid cropping for illustration image {}x{}", width, height);

        long gridBoundsStart = System.currentTimeMillis();
        GridBounds gridBounds = detectGridBounds(originalImage);
        timing.gridBoundsDetectionMs = System.currentTimeMillis() - gridBoundsStart;

        // First pass: collect all cropped illustrations and find the maximum dimensions
        List<BufferedImage> croppedImages = new ArrayList<>();
        int maxContentWidth = 0;
        int maxContentHeight = 0;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                Rectangle illustrationRect = gridBounds.getIllustrationRectangle(row, col);

                log.debug("Cropping illustration at position [{},{}] with bounds: x={}, y={}, width={}, height={}",
                        row, col, illustrationRect.x, illustrationRect.y, illustrationRect.width, illustrationRect.height);

                BufferedImage croppedIllustration = originalImage.getSubimage(
                        illustrationRect.x, illustrationRect.y, illustrationRect.width, illustrationRect.height);

                // Remove transparent borders by detecting and cropping to actual content bounds
                long contentBoundsStart = System.currentTimeMillis();
                Rectangle contentBounds = detectContentBounds(croppedIllustration);
                timing.contentBoundsDetectionMs += System.currentTimeMillis() - contentBoundsStart;

                if (contentBounds != null && contentBounds.width > 0 && contentBounds.height > 0) {
                    croppedIllustration = croppedIllustration.getSubimage(
                            contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);
                    log.debug("Removed transparent borders from illustration [{},{}]: content size {}x{}",
                            row, col, contentBounds.width, contentBounds.height);

                    // Track maximum dimensions to ensure uniform sizing
                    maxContentWidth = Math.max(maxContentWidth, croppedIllustration.getWidth());
                    maxContentHeight = Math.max(maxContentHeight, croppedIllustration.getHeight());
                } else {
                    log.warn("No content detected in illustration [{},{}], using original crop", row, col);
                }

                croppedImages.add(croppedIllustration);
            }
        }

        // Determine final target dimensions
        int finalWidth, finalHeight;
        if (targetSize > 0) {
            // Use specified target size, maintaining aspect ratio of largest content
            double aspectRatio = (double) maxContentWidth / maxContentHeight;
            if (aspectRatio > 1.0) {
                // Wider than tall
                finalWidth = targetSize;
                finalHeight = (int) (targetSize / aspectRatio);
            } else {
                // Taller than wide
                finalHeight = targetSize;
                finalWidth = (int) (targetSize * aspectRatio);
            }
        } else {
            // Use maximum dimensions found
            finalWidth = maxContentWidth;
            finalHeight = maxContentHeight;
        }

        log.info("All illustrations will be resized to uniform size: {}x{} (no transparent borders)",
                finalWidth, finalHeight);

        // Second pass: resize all illustrations to the same dimensions
        for (int i = 0; i < croppedImages.size(); i++) {
            long resizingStart = System.currentTimeMillis();
            BufferedImage uniformIllustration = resizeToFill(croppedImages.get(i), finalWidth, finalHeight);
            timing.illustrationResizingMs += System.currentTimeMillis() - resizingStart;
            String base64Illustration = bufferedImageToBase64(uniformIllustration);
            illustrations.add(base64Illustration);
            log.debug("Processed illustration {} to final size {}x{}", i, finalWidth, finalHeight);
        }

        return illustrations;
    }

    /**
     * Log a comprehensive summary of processing timing for performance analysis
     */
    private void logProcessingTimingSummary(ProcessingTiming timing, long totalProcessingTime, int illustrationCount, int imageWidth, int imageHeight) {
        log.info("🚀 ILLUSTRATION PROCESSING PERFORMANCE SUMMARY 🚀");
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ Image: {}x{} pixels, Processing {} illustrations", imageWidth, imageHeight, illustrationCount);
        log.info("├─────────────────────────────────────────────────────────────");
        log.info("│ ⏱️  TIMING BREAKDOWN:");
        log.info(String.format("│   • Upscaling:              %6d ms  (%5.1f%%)",
                timing.upscaleMs, getPercentage(timing.upscaleMs, totalProcessingTime)));
        log.info(String.format("│   • Background Removal:     %6d ms  (%5.1f%%)",
                timing.backgroundRemovalMs, getPercentage(timing.backgroundRemovalMs, totalProcessingTime)));
        log.info(String.format("│   • Image Parsing:          %6d ms  (%5.1f%%)",
                timing.imageParsingMs, getPercentage(timing.imageParsingMs, totalProcessingTime)));
        log.info(String.format("│   • Solid Frame Detection:  %6d ms  (%5.1f%%)",
                timing.solidFrameDetectionMs, getPercentage(timing.solidFrameDetectionMs, totalProcessingTime)));
        log.info(String.format("│   • Grid Bounds Detection:  %6d ms  (%5.1f%%)",
                timing.gridBoundsDetectionMs, getPercentage(timing.gridBoundsDetectionMs, totalProcessingTime)));
        log.info(String.format("│   • Content Bounds Detection: %6d ms  (%5.1f%%)",
                timing.contentBoundsDetectionMs, getPercentage(timing.contentBoundsDetectionMs, totalProcessingTime)));
        log.info(String.format("│   • Illustration Resizing:  %6d ms  (%5.1f%%)",
                timing.illustrationResizingMs, getPercentage(timing.illustrationResizingMs, totalProcessingTime)));
        log.info("├─────────────────────────────────────────────────────────────");
        log.info(String.format("│   🎯 MEASURED TOTAL:         %6d ms  (%5.1f%%)",
                timing.getTotalMs(), getPercentage(timing.getTotalMs(), totalProcessingTime)));
        log.info(String.format("│   ⚡ ACTUAL TOTAL:           %6d ms  (100.0%%)", totalProcessingTime));
        log.info(String.format("│   📊 OVERHEAD:               %6d ms  (%5.1f%%)",
                totalProcessingTime - timing.getTotalMs(),
                getPercentage(totalProcessingTime - timing.getTotalMs(), totalProcessingTime)));
        log.info("├─────────────────────────────────────────────────────────────");
        log.info("│ 📈 PERFORMANCE METRICS:");
        log.info(String.format("│   • Processing rate:        %6.1f ms/illustration", (double) totalProcessingTime / illustrationCount));
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

    /**
     * Resize an image to exactly fill the target dimensions without transparent borders
     * The image will be scaled to fill the entire canvas (may be slightly cropped if aspect ratios don't match)
     */
    private BufferedImage resizeToFill(BufferedImage sourceImage, int targetWidth, int targetHeight) {
        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();

        // Calculate scale to fill the target dimensions completely
        double scaleX = (double) targetWidth / sourceWidth;
        double scaleY = (double) targetHeight / sourceHeight;
        double scale = Math.max(scaleX, scaleY); // Use max to ensure we fill the entire canvas

        int scaledWidth = (int) (sourceWidth * scale);
        int scaledHeight = (int) (sourceHeight * scale);

        // Create result image at exact target size
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();

        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Center the scaled image (may crop edges if scale > 1.0 in one direction)
        int x = (targetWidth - scaledWidth) / 2;
        int y = (targetHeight - scaledHeight) / 2;

        g2d.drawImage(sourceImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();

        return result;
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
     * Detect grid bounds for 2x2 grid with white boundaries
     */
    private GridBounds detectGridBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        log.debug("Detecting intelligent grid bounds for 2x2 illustration grid: {}x{}", width, height);

        // Try to detect white boundaries first
        int[] xBoundaries = detectWhiteLineBoundaries(image, true);
        int[] yBoundaries = detectWhiteLineBoundaries(image, false);

        // Fall back to simple division if white boundaries not found
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
     * Detect grid boundaries by looking for white pixels (for 2x2 grid)
     */
    private int[] detectWhiteLineBoundaries(BufferedImage image, boolean vertical) {
        int dimension = vertical ? image.getWidth() : image.getHeight();

        log.debug("Detecting white {} boundaries for 2x2 grid, dimension: {}",
                vertical ? "vertical" : "horizontal", dimension);

        // For 2x2 grid, we only need to find one boundary (at the middle)
        int idealBoundary = dimension / 2;
        int searchRange = dimension / 10; // Search within 10% of center

        // Find best white line near the ideal center position
        int bestBoundary = findBestWhiteLine(image, vertical,
                Math.max(1, idealBoundary - searchRange),
                Math.min(dimension - 1, idealBoundary + searchRange));

        if (bestBoundary == -1) {
            log.debug("Could not find white {} boundary, using simple division",
                    vertical ? "vertical" : "horizontal");
            return null;
        }

        log.debug("Found white {} boundary at position {}",
                vertical ? "vertical" : "horizontal", bestBoundary);

        return new int[]{0, bestBoundary, dimension};
    }

    /**
     * Find the line with the highest whiteness percentage within a given range
     */
    private int findBestWhiteLine(BufferedImage image, boolean vertical, int startPos, int endPos) {
        int bestPosition = -1;
        double bestWhiteness = 0.0;
        double minWhiteness = 0.90; // Minimum 90% whiteness

        for (int pos = startPos; pos <= endPos; pos++) {
            double whiteness = calculateLineWhiteness(image, vertical, pos);

            if (whiteness >= minWhiteness && whiteness > bestWhiteness) {
                bestWhiteness = whiteness;
                bestPosition = pos;
            }
        }

        if (bestPosition != -1) {
            log.debug("Best white line at position {} with {}% whiteness",
                    bestPosition, String.format("%.1f", bestWhiteness * 100));
        }

        return bestPosition;
    }

    /**
     * Calculate the percentage of white pixels along a line
     */
    private double calculateLineWhiteness(BufferedImage image, boolean vertical, int position) {
        int dimension = vertical ? image.getHeight() : image.getWidth();
        int whitePixels = 0;
        int totalPixels = dimension;

        for (int i = 0; i < dimension; i++) {
            int rgb = vertical ? image.getRGB(position, i) : image.getRGB(i, position);
            int alpha = (rgb >> 24) & 0xFF;
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;

            // Check for opaque white pixel (with some tolerance)
            if (alpha > 250 && red > 250 && green > 250 && blue > 250) {
                whitePixels++;
            }
        }

        return (double) whitePixels / totalPixels;
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

        double maxContentWidth = canvasWidth;// * 0.9; // Use 90% of canvas width
        double maxContentHeight = canvasHeight;// * 0.9; // Use 90% of canvas height
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
