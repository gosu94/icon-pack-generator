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
import java.util.concurrent.*;
import java.util.stream.Collectors;

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
        public long artifactCleanupMs = 0;
        public long contentBoundsDetectionMs = 0;
        public long illustrationResizingMs = 0;
        public long upscaleMs = 0;

        public long getTotalMs() {
            return backgroundRemovalMs + imageParsingMs + solidFrameDetectionMs +
                    gridBoundsDetectionMs + artifactCleanupMs + contentBoundsDetectionMs + 
                    illustrationResizingMs + upscaleMs;
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
     * Helper class to hold illustration processing task result
     */
    private static class IllustrationProcessingResult {
        final int index;
        final BufferedImage processedImage;
        final long artifactCleanupMs;
        final long contentBoundsMs;
        
        IllustrationProcessingResult(int index, BufferedImage image, long artifactCleanupMs, long contentBoundsMs) {
            this.index = index;
            this.processedImage = image;
            this.artifactCleanupMs = artifactCleanupMs;
            this.contentBoundsMs = contentBoundsMs;
        }
    }

    /**
     * Crop a 2x2 grid of illustrations with parallel processing for better performance
     * All illustrations will have the same size with no transparent borders
     */
    private List<String> crop2x2Grid(BufferedImage originalImage, boolean centerIllustrations, int targetSize, ProcessingTiming timing) throws IOException {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        log.info("Starting intelligent 2x2 grid cropping for illustration image {}x{} with parallel processing", width, height);

        long gridBoundsStart = System.currentTimeMillis();
        GridBounds gridBounds = detectGridBounds(originalImage);
        timing.gridBoundsDetectionMs = System.currentTimeMillis() - gridBoundsStart;

        // Create a thread pool for parallel processing (4 quadrants)
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        try {
            // Process all 4 quadrants in parallel
            List<CompletableFuture<IllustrationProcessingResult>> futures = new ArrayList<>();
            
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    final int finalRow = row;
                    final int finalCol = col;
                    final int index = row * 2 + col;
                    
                    Rectangle illustrationRect = gridBounds.getIllustrationRectangle(row, col);
                    
                    // Create a copy of the subimage for thread safety
                    BufferedImage subImage = deepCopyBufferedImage(originalImage.getSubimage(
                            illustrationRect.x, illustrationRect.y, illustrationRect.width, illustrationRect.height));
                    
                    // Submit processing task for this quadrant
                    CompletableFuture<IllustrationProcessingResult> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            log.debug("Processing illustration [{},{}] in parallel thread", finalRow, finalCol);
                            
                            // Clean up artifacts
                            long artifactCleanupStart = System.currentTimeMillis();
                            BufferedImage cleaned = cleanupIllustrationArtifacts(subImage, finalRow, finalCol);
                            long artifactCleanupTime = System.currentTimeMillis() - artifactCleanupStart;
                            
                            // Detect content bounds
                            long contentBoundsStart = System.currentTimeMillis();
                            Rectangle contentBounds = detectContentBounds(cleaned);
                            long contentBoundsTime = System.currentTimeMillis() - contentBoundsStart;
                            
                            if (contentBounds != null && contentBounds.width > 0 && contentBounds.height > 0) {
                                cleaned = cleaned.getSubimage(
                                        contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);
                                log.debug("Processed illustration [{},{}]: content size {}x{}",
                                        finalRow, finalCol, contentBounds.width, contentBounds.height);
                            }
                            
                            return new IllustrationProcessingResult(index, cleaned, artifactCleanupTime, contentBoundsTime);
                            
                        } catch (Exception e) {
                            log.error("Error processing illustration [{},{}]", finalRow, finalCol, e);
                            return new IllustrationProcessingResult(index, subImage, 0, 0);
                        }
                    }, executor);
                    
                    futures.add(future);
                }
            }
            
            // Wait for all tasks to complete and collect results
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            
            allOf.join(); // Wait for all to complete
            
            // Collect results in order
            List<IllustrationProcessingResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted((a, b) -> Integer.compare(a.index, b.index))
                    .collect(Collectors.toList());
            
            // Accumulate timing
            for (IllustrationProcessingResult result : results) {
                timing.artifactCleanupMs += result.artifactCleanupMs;
                timing.contentBoundsDetectionMs += result.contentBoundsMs;
            }
            
            log.info("Parallel processing completed for all 4 quadrants");
            
            // First pass: find maximum dimensions
            List<BufferedImage> croppedImages = new ArrayList<>();
            int maxContentWidth = 0;
            int maxContentHeight = 0;
            
            for (IllustrationProcessingResult result : results) {
                BufferedImage img = result.processedImage;
                croppedImages.add(img);
                maxContentWidth = Math.max(maxContentWidth, img.getWidth());
                maxContentHeight = Math.max(maxContentHeight, img.getHeight());
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
            List<String> illustrations = new ArrayList<>();
            for (int i = 0; i < croppedImages.size(); i++) {
                long resizingStart = System.currentTimeMillis();
                BufferedImage uniformIllustration = resizeToFill(croppedImages.get(i), finalWidth, finalHeight);
                timing.illustrationResizingMs += System.currentTimeMillis() - resizingStart;
                String base64Illustration = bufferedImageToBase64(uniformIllustration);
                illustrations.add(base64Illustration);
                log.debug("Processed illustration {} to final size {}x{}", i, finalWidth, finalHeight);
            }

            return illustrations;
            
        } finally {
            // Shutdown executor and wait for all tasks to complete
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Create a deep copy of a BufferedImage for thread-safe parallel processing
     */
    private BufferedImage deepCopyBufferedImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(source, 0, 0, null);
        g2d.dispose();
        return copy;
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
        log.info(String.format("│   • Artifact Cleanup:       %6d ms  (%5.1f%%)",
                timing.artifactCleanupMs, getPercentage(timing.artifactCleanupMs, totalProcessingTime)));
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
     * Detect grid bounds for 2x2 grid by splitting exactly in half
     * This replaces the white line detection approach which often fails when the entire image has grid lines
     */
    private GridBounds detectGridBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        log.debug("Detecting grid bounds for 2x2 illustration grid: {}x{}", width, height);
        log.debug("Using exact half-split method (no white line detection)");

        // Split exactly in half for both dimensions
        int[] xBoundaries = new int[]{0, width / 2, width};
        int[] yBoundaries = new int[]{0, height / 2, height};

        log.debug("X boundaries (exact half): {}", java.util.Arrays.toString(xBoundaries));
        log.debug("Y boundaries (exact half): {}", java.util.Arrays.toString(yBoundaries));

        // Convert boundaries to GridBounds format
        int[][] xBounds = new int[2][2]; // [column][start/end]
        int[][] yBounds = new int[2][2]; // [row][start/end]

        for (int i = 0; i < 2; i++) {
            xBounds[i][0] = xBoundaries[i];
            xBounds[i][1] = xBoundaries[i + 1];
            yBounds[i][0] = yBoundaries[i];
            yBounds[i][1] = yBoundaries[i + 1];
        }

        log.debug("Grid bounds set - Columns: [{},{}] and [{},{}], Rows: [{},{}] and [{},{}]",
                xBounds[0][0], xBounds[0][1], xBounds[1][0], xBounds[1][1],
                yBounds[0][0], yBounds[0][1], yBounds[1][0], yBounds[1][1]);

        return new GridBounds(xBounds, yBounds);
    }

    /**
     * Clean up artifacts from a cropped illustration by detecting grey/semi-transparent frames at edges
     * and replacing them with white background. This is a simplified, fast approach.
     *
     * @param croppedIllustration The cropped illustration image that may contain artifacts
     * @param row                 The row position of this illustration in the 2x2 grid (0-1)
     * @param col                 The column position of this illustration in the 2x2 grid (0-1)
     * @return The cleaned illustration with white background replacing any edge frames
     */
    private BufferedImage cleanupIllustrationArtifacts(BufferedImage croppedIllustration, int row, int col) {
        int width = croppedIllustration.getWidth();
        int height = croppedIllustration.getHeight();

        log.debug("Cleaning up edge frames for illustration at [{},{}] with size {}x{}", row, col, width, height);

        // Convert to ARGB if not already
        BufferedImage cleanedIllustration = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = cleanedIllustration.createGraphics();
        g2d.drawImage(croppedIllustration, 0, 0, null);
        g2d.dispose();

        // Define maximum edge thickness to check - grid lines are typically thin (1-10 pixels max)
        int maxEdgeThickness = 20; // Conservative limit to avoid removing content
        
        int pixelsReplaced = 0;

        // Check and clean each edge based on grid position
        if (col > 0) { // Not leftmost column - check left edge
            pixelsReplaced += detectAndReplaceEdgeFrame(cleanedIllustration, "left", maxEdgeThickness);
        }
        
        if (col < 1) { // Not rightmost column - check right edge
            pixelsReplaced += detectAndReplaceEdgeFrame(cleanedIllustration, "right", maxEdgeThickness);
        }
        
        if (row > 0) { // Not top row - check top edge
            pixelsReplaced += detectAndReplaceEdgeFrame(cleanedIllustration, "top", maxEdgeThickness);
        }
        
        if (row < 1) { // Not bottom row - check bottom edge
            pixelsReplaced += detectAndReplaceEdgeFrame(cleanedIllustration, "bottom", maxEdgeThickness);
        }

        log.debug("Replaced {} edge frame pixels with white for illustration at [{},{}]", pixelsReplaced, row, col);
        return cleanedIllustration;
    }

    /**
     * Detect and replace grey or semi-transparent frame pixels on a specific edge with opaque white.
     * This uses a two-phase approach:
     * 1. Detect if there's a consistent grid line at the very edge
     * 2. Only replace pixels that match the detected line's characteristics
     *
     * @param illustration     The illustration image to process
     * @param edge            The edge to check: "top", "bottom", "left", or "right"
     * @param maxThickness    Maximum thickness to check from the edge
     * @return Number of pixels replaced with white
     */
    private int detectAndReplaceEdgeFrame(BufferedImage illustration, String edge, int maxThickness) {
        int width = illustration.getWidth();
        int height = illustration.getHeight();
        int pixelsReplaced = 0;
        
        // Phase 1: Detect the dominant edge line color by sampling the very first pixels
        Integer dominantLineColor = detectEdgeLineColor(illustration, edge);
        
        if (dominantLineColor == null) {
            return 0; // No consistent grid line detected at the edge
        }
        
        log.debug("Detected {} edge line with color RGB({},{},{})", edge, 
                (dominantLineColor >> 16) & 0xFF, (dominantLineColor >> 8) & 0xFF, dominantLineColor & 0xFF);
        
        // Phase 2: Detect frame thickness by scanning for pixels matching the line color
        int frameThickness = detectFrameThicknessForColor(illustration, edge, maxThickness, dominantLineColor);
        
        if (frameThickness == 0) {
            return 0; // No frame detected
        }
        
        log.debug("Detected {} edge frame of thickness {}", edge, frameThickness);
        
        // Phase 3: Replace pixels that match the detected line color with opaque white
        int whiteRGB = 0xFFFFFFFF; // Opaque white
        
        switch (edge.toLowerCase()) {
            case "top":
                for (int y = 0; y < frameThickness && y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        if (isMatchingLineColor(illustration.getRGB(x, y), dominantLineColor)) {
                            illustration.setRGB(x, y, whiteRGB);
                            pixelsReplaced++;
                        }
                    }
                }
                break;
                
            case "bottom":
                for (int y = Math.max(0, height - frameThickness); y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        if (isMatchingLineColor(illustration.getRGB(x, y), dominantLineColor)) {
                            illustration.setRGB(x, y, whiteRGB);
                            pixelsReplaced++;
                        }
                    }
                }
                break;
                
            case "left":
                for (int x = 0; x < frameThickness && x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        if (isMatchingLineColor(illustration.getRGB(x, y), dominantLineColor)) {
                            illustration.setRGB(x, y, whiteRGB);
                            pixelsReplaced++;
                        }
                    }
                }
                break;
                
            case "right":
                for (int x = Math.max(0, width - frameThickness); x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        if (isMatchingLineColor(illustration.getRGB(x, y), dominantLineColor)) {
                            illustration.setRGB(x, y, whiteRGB);
                            pixelsReplaced++;
                        }
                    }
                }
                break;
        }
        
        return pixelsReplaced;
    }
    
    /**
     * Detect the dominant color of the grid line at the edge of the image.
     * Samples the first few pixels (up to 5 deep) from the edge to get a better color profile.
     * This handles grid lines that have varying colors (e.g., black core with grey anti-aliasing edges).
     *
     * @param image The image to analyze
     * @param edge  The edge to check
     * @return The dominant RGB color of the line, or null if no consistent line detected
     */
    private Integer detectEdgeLineColor(BufferedImage image, String edge) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        List<Integer> edgeColors = new ArrayList<>();
        
        // Sample pixels at the edge - go 3-5 pixels deep to get full color profile
        int sampleDepth = Math.min(5, Math.min(width, height) / 20);
        
        switch (edge.toLowerCase()) {
            case "top":
                // Sample first few rows
                for (int y = 0; y < sampleDepth; y++) {
                    for (int x = 0; x < width; x += Math.max(1, width / 100)) {
                        int rgb = image.getRGB(x, y);
                        if (isFramePixel(rgb)) {
                            edgeColors.add(rgb & 0x00FFFFFF); // Store RGB only (ignore alpha for comparison)
                        }
                    }
                }
                break;
                
            case "bottom":
                // Sample last few rows
                for (int y = height - sampleDepth; y < height; y++) {
                    for (int x = 0; x < width; x += Math.max(1, width / 100)) {
                        int rgb = image.getRGB(x, y);
                        if (isFramePixel(rgb)) {
                            edgeColors.add(rgb & 0x00FFFFFF);
                        }
                    }
                }
                break;
                
            case "left":
                // Sample first few columns
                for (int x = 0; x < sampleDepth; x++) {
                    for (int y = 0; y < height; y += Math.max(1, height / 100)) {
                        int rgb = image.getRGB(x, y);
                        if (isFramePixel(rgb)) {
                            edgeColors.add(rgb & 0x00FFFFFF);
                        }
                    }
                }
                break;
                
            case "right":
                // Sample last few columns
                for (int x = width - sampleDepth; x < width; x++) {
                    for (int y = 0; y < height; y += Math.max(1, height / 100)) {
                        int rgb = image.getRGB(x, y);
                        if (isFramePixel(rgb)) {
                            edgeColors.add(rgb & 0x00FFFFFF);
                        }
                    }
                }
                break;
        }
        
        // Need at least 30% of samples to be frame pixels to consider it a line
        // Lower threshold to catch lines that might be partially obscured or inconsistent
        int samplesPerLine = (edge.equals("top") || edge.equals("bottom")) ? 
                Math.max(1, width / 100) : Math.max(1, height / 100);
        int totalSamples = samplesPerLine * sampleDepth; // Total samples across all depth
        
        if (edgeColors.size() < totalSamples * 0.3) {
            return null; // Not enough frame pixels - probably not a grid line
        }
        
        // Calculate the average color of the detected frame pixels
        int totalRed = 0, totalGreen = 0, totalBlue = 0;
        for (Integer rgb : edgeColors) {
            totalRed += (rgb >> 16) & 0xFF;
            totalGreen += (rgb >> 8) & 0xFF;
            totalBlue += rgb & 0xFF;
        }
        
        int avgRed = totalRed / edgeColors.size();
        int avgGreen = totalGreen / edgeColors.size();
        int avgBlue = totalBlue / edgeColors.size();
        
        return (avgRed << 16) | (avgGreen << 8) | avgBlue;
    }
    
    /**
     * Detect frame thickness for pixels matching a specific line color
     */
    private int detectFrameThicknessForColor(BufferedImage image, String edge, int maxThickness, int targetColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        for (int thickness = 1; thickness <= maxThickness; thickness++) {
            int matchingPixelCount = 0;
            int totalPixels = 0;
            
            // Sample pixels along the line at current thickness
            switch (edge.toLowerCase()) {
                case "top":
                    if (thickness > height) return thickness - 1;
                    for (int x = 0; x < width; x += Math.max(1, width / 50)) {
                        totalPixels++;
                        if (isMatchingLineColor(image.getRGB(x, thickness - 1), targetColor)) {
                            matchingPixelCount++;
                        }
                    }
                    break;
                    
                case "bottom":
                    if (thickness > height) return thickness - 1;
                    for (int x = 0; x < width; x += Math.max(1, width / 50)) {
                        totalPixels++;
                        if (isMatchingLineColor(image.getRGB(x, height - thickness), targetColor)) {
                            matchingPixelCount++;
                        }
                    }
                    break;
                    
                case "left":
                    if (thickness > width) return thickness - 1;
                    for (int y = 0; y < height; y += Math.max(1, height / 50)) {
                        totalPixels++;
                        if (isMatchingLineColor(image.getRGB(thickness - 1, y), targetColor)) {
                            matchingPixelCount++;
                        }
                    }
                    break;
                    
                case "right":
                    if (thickness > width) return thickness - 1;
                    for (int y = 0; y < height; y += Math.max(1, height / 50)) {
                        totalPixels++;
                        if (isMatchingLineColor(image.getRGB(width - thickness, y), targetColor)) {
                            matchingPixelCount++;
                        }
                    }
                    break;
                    
                default:
                    return 0;
            }
            
            // Check if this line has predominantly matching pixels
            double matchRatio = totalPixels > 0 ? (double) matchingPixelCount / totalPixels : 0.0;
            
            // If less than 40% match, we've reached the end of the frame
            // Lower threshold to handle lines with some gaps or variations
            if (matchRatio < 0.40) {
                return thickness - 1;
            }
        }
        
        // All checked lines were matching pixels
        return maxThickness;
    }
    
    /**
     * Check if a pixel color matches the detected line color (within tolerance)
     */
    private boolean isMatchingLineColor(int pixelRGB, int lineRGB) {
        int pixelRed = (pixelRGB >> 16) & 0xFF;
        int pixelGreen = (pixelRGB >> 8) & 0xFF;
        int pixelBlue = pixelRGB & 0xFF;
        
        int lineRed = (lineRGB >> 16) & 0xFF;
        int lineGreen = (lineRGB >> 8) & 0xFF;
        int lineBlue = lineRGB & 0xFF;
        
        // Colors must be within 40 units of the line color in each channel
        // High tolerance to handle full range of grid line variations:
        // - Black core (0,0,0) to light grey edges (40,40,40)
        // - Anti-aliasing, compression artifacts, and blending
        int colorTolerance = 40;
        
        return Math.abs(pixelRed - lineRed) <= colorTolerance &&
               Math.abs(pixelGreen - lineGreen) <= colorTolerance &&
               Math.abs(pixelBlue - lineBlue) <= colorTolerance;
    }
    
    /**
     * Check if a pixel is a frame artifact (grey, black, semi-transparent, or near-white).
     * Frame pixels are typically:
     * - Any shade of grey (including black) where RGB values are similar to each other
     * - Semi-transparent pixels (alpha < 255 but > 0)
     * - Very light colors that aren't pure white (includes subtle greys)
     * - Anything that's not pure white (255,255,255,255) or actual content colors
     *
     * @param rgb The RGB value of the pixel
     * @return true if the pixel appears to be a frame artifact
     */
    private boolean isFramePixel(int rgb) {
        int alpha = (rgb >> 24) & 0xFF;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        
        // Check for semi-transparent pixels (common in grid lines)
        if (alpha > 10 && alpha < 250) {
            return true;
        }
        
        // Skip pure white pixels (the target background)
        if (alpha == 255 && red == 255 && green == 255 && blue == 255) {
            return false;
        }
        
        // Check if RGB values are similar (greyish/neutral color)
        int maxDiff = Math.max(Math.abs(red - green), Math.max(Math.abs(green - blue), Math.abs(red - blue)));
        boolean isGreyish = maxDiff < 40; // RGB values within 40 of each other (increased tolerance)
        
        if (!isGreyish) {
            return false; // Not a neutral grey/black/white color - likely actual content
        }
        
        // At this point, pixel is greyish (neutral color)
        int avgColor = (red + green + blue) / 3;
        
        // Black frames: very dark greys/blacks
        if (avgColor < 50) {
            return true;
        }
        
        // Medium grey frames: typical grey grid lines
        if (avgColor >= 50 && avgColor < 240) {
            return true;
        }
        
        // Subtle grey frames: very light greys that are close to but not exactly white
        // This catches subtle differences like RGB(240,240,240) or RGB(250,250,250)
        if (avgColor >= 240 && avgColor < 255) {
            // Check if it's actually slightly different from pure white
            if (red < 255 || green < 255 || blue < 255) {
                return true;
            }
        }
        
        return false;
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
