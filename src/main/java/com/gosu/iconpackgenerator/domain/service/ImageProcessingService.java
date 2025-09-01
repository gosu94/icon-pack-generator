package com.gosu.iconpackgenerator.domain.service;

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

    private final BackgroundRemovalService backgroundRemovalService;

    /**
     * Crop a 3x3 grid of icons from the generated image
     *
     * @param imageData The original image as byte array
     * @param iconCount The number of icons to extract (9 or 18)
     * @return List of cropped icon images as base64 strings
     */
    public List<String> cropIconsFromGrid(byte[] imageData, int iconCount, boolean removeBackground) {
        return cropIconsFromGrid(imageData, iconCount, true, 0, removeBackground);
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
        return cropIconsFromGrid(imageData, iconCount, centerIcons, targetSize, false);
    }

    /**
     * Crop a 3x3 grid of icons from the generated image with optional centering and background removal
     *
     * @param imageData        The original image as byte array
     * @param iconCount        The number of icons to extract (9 or 18)
     * @param centerIcons      Whether to center the icons on their canvas
     * @param targetSize       Target size for centered icons (0 = auto-size based on original)
     * @param removeBackground Whether to remove background from the entire grid before cropping (typically false during generation, true during export)
     * @return List of cropped icon images as base64 strings
     */
    public List<String> cropIconsFromGrid(byte[] imageData, int iconCount, boolean centerIcons, int targetSize, boolean removeBackground) {
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

            // Optionally remove background from the entire grid image before processing
            byte[] processedImageData = imageData;
            if (removeBackground) {
                try {
                    // Heuristic check to see if background is already transparent
                    BufferedImage imageToCheck = ImageIO.read(new ByteArrayInputStream(imageData));
                    if (imageToCheck != null && hasTransparentBackground(imageToCheck)) {
                        log.info("Image background appears to be already transparent. Skipping background removal.");
                    } else {
                        if (imageToCheck == null) {
                            log.warn("Could not read image to check for transparency, proceeding with background removal.");
                        }
                        log.info("Removing background from grid image before cropping icons");
                        processedImageData = backgroundRemovalService.removeBackground(imageData);

                        if (processedImageData.length != imageData.length) {
                            log.info("Background removal changed image size from {} to {} bytes",
                                    imageData.length, processedImageData.length);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error during transparency check, proceeding with background removal.", e);
                    processedImageData = backgroundRemovalService.removeBackground(imageData);
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

            // Add image diagnostics
            performImageDiagnostics(originalImage, processedImageData.length);

            List<String> croppedIcons = new ArrayList<>();

            if (iconCount == 9) {
                croppedIcons.addAll(cropGrid3x3(originalImage, centerIcons, targetSize));
            } else if (iconCount == 18) {
                // For 18 icons, we'll need to generate two 3x3 grids
                // For now, we'll crop first 9 from one grid
                croppedIcons.addAll(cropGrid3x3(originalImage, centerIcons, targetSize));
                // TODO: Handle second grid generation for 18 icons
            }

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
     * Center an icon by detecting its content bounding box and placing it centered on a square canvas
     *
     * @param iconImage  The original icon image
     * @param targetSize The size of the square canvas (if 0, uses the original image's larger dimension)
     * @return The centered icon as a BufferedImage
     */
    public BufferedImage centerIcon(BufferedImage iconImage, int targetSize) {
        if (iconImage == null) {
            throw new IllegalArgumentException("Icon image cannot be null");
        }

        log.debug("Centering icon of size {}x{}", iconImage.getWidth(), iconImage.getHeight());

        // Step 1: Detect the bounding box of non-white/non-transparent pixels
        Rectangle contentBounds = detectContentBounds(iconImage);

        if (contentBounds == null || contentBounds.width == 0 || contentBounds.height == 0) {
            log.warn("No content detected in icon, returning original image");
            return iconImage;
        }

        log.debug("Content bounds detected: x={}, y={}, width={}, height={}",
                contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);

        // Step 2: Crop to the content bounding box
        BufferedImage croppedContent = iconImage.getSubimage(
                contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);

        // Step 3: Determine target canvas size
        if (targetSize <= 0) {
            targetSize = Math.max(iconImage.getWidth(), iconImage.getHeight());
        }

        // Step 4: Create a new square canvas and center the content
        BufferedImage centeredIcon = createCenteredCanvas(croppedContent, targetSize);

        log.debug("Successfully centered icon to {}x{} canvas", targetSize, targetSize);
        return centeredIcon;
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
     * Detect the bounding box of all non-background pixels (excludes transparent, white, and very dark pixels)
     *
     * @param image The image to analyze
     * @return Rectangle representing the bounding box, or null if no content found
     */
    private Rectangle detectContentBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        boolean hasContent = false;
        int totalPixels = width * height;
        int transparentPixels = 0;
        int whitePixels = 0;
        int blackPixels = 0;
        int contentPixels = 0;

        log.debug("Analyzing image {}x{} for content bounds", width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);

                // Extract alpha, red, green, blue components
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                // Check pixel type
                boolean isTransparent = alpha < 10; // Almost fully transparent
                boolean isWhite = red > 245 && green > 245 && blue > 245; // Almost white
                boolean isBlack = red < 10 && green < 10 && blue < 10; // Almost black

                // Count pixel types for diagnostics
                if (isTransparent) {
                    transparentPixels++;
                } else if (isWhite) {
                    whitePixels++;
                } else if (isBlack) {
                    blackPixels++;
                } else {
                    contentPixels++;
                }

                // Consider as content if it's not transparent, not pure white, and not pure black
                if (!isTransparent && !isWhite && !isBlack) {
                    hasContent = true;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        // Log diagnostics
        log.debug("Image analysis: total={}, transparent={}, white={}, black={}, content={}",
                totalPixels, transparentPixels, whitePixels, blackPixels, contentPixels);

        if (!hasContent) {
            log.warn("No content detected in image. Image might be entirely black, white, or transparent. " +
                            "Pixel distribution: {}% transparent, {}% white, {}% black, {}% content",
                    Math.round(100.0 * transparentPixels / totalPixels),
                    Math.round(100.0 * whitePixels / totalPixels),
                    Math.round(100.0 * blackPixels / totalPixels),
                    Math.round(100.0 * contentPixels / totalPixels));

            // If the image is mostly black, treat the black pixels as content
            if (blackPixels > totalPixels * 0.1) { // More than 10% black pixels
                log.debug("Image appears to be mostly black. Treating black pixels as content for centering.");
                return detectContentBoundsIncludingBlack(image);
            }

            return null;
        }

        // Return bounding rectangle
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * Alternative content detection that includes black pixels as content
     * Used when regular detection fails but image has significant black content
     */
    private Rectangle detectContentBoundsIncludingBlack(BufferedImage image) {
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

                // Extract alpha, red, green, blue components
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                // Check if pixel is not transparent and not white (including black as content)
                boolean isTransparent = alpha < 10; // Almost fully transparent
                boolean isWhite = red > 245 && green > 245 && blue > 245; // Almost white

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
            log.warn("Even with black pixels included, no content detected in image");
            return null;
        }

        log.debug("Content bounds detected including black pixels: x={}, y={}, width={}, height={}",
                minX, minY, maxX - minX + 1, maxY - minY + 1);

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
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

    /**
     * Create a new square canvas and center the given image on it
     *
     * @param contentImage The image to center
     * @param canvasSize   The size of the square canvas
     * @return New BufferedImage with the content centered
     */
    private BufferedImage createCenteredCanvas(BufferedImage contentImage, int canvasSize) {
        // Create a new square canvas with transparent background
        BufferedImage canvas = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = canvas.createGraphics();

        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill with transparent background
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, canvasSize, canvasSize);
        g2d.setComposite(AlphaComposite.SrcOver);

        // Calculate position to center the content
        int contentWidth = contentImage.getWidth();
        int contentHeight = contentImage.getHeight();

        // Scale content if it's larger than the canvas (with some padding)
        double maxContentSize = canvasSize * 0.9; // Use 90% of canvas to leave some padding
        double scale = 1.0;

        if (contentWidth > maxContentSize || contentHeight > maxContentSize) {
            double scaleX = maxContentSize / contentWidth;
            double scaleY = maxContentSize / contentHeight;
            scale = Math.min(scaleX, scaleY);
        }

        int scaledWidth = (int) (contentWidth * scale);
        int scaledHeight = (int) (contentHeight * scale);

        int x = (canvasSize - scaledWidth) / 2;
        int y = (canvasSize - scaledHeight) / 2;

        // Draw the content centered on the canvas
        g2d.drawImage(contentImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();

        return canvas;
    }

    /**
     * Helper class to represent intelligent grid bounds for a 3x3 icon grid
     */
    private static class GridBounds {
        private final int[][] xBounds; // [column][start/end] - x coordinates for each column
        private final int[][] yBounds; // [row][start/end] - y coordinates for each row

        public GridBounds(int[][] xBounds, int[][] yBounds) {
            this.xBounds = xBounds;
            this.yBounds = yBounds;
        }

        public Rectangle getIconRectangle(int row, int col) {
            int x = xBounds[col][0];
            int y = yBounds[row][0];
            int width = xBounds[col][1] - xBounds[col][0];
            int height = yBounds[row][1] - yBounds[row][0];
            return new Rectangle(x, y, width, height);
        }
    }

    /**
     * Helper class to track boundary detection results and quality
     */
    private static class BoundaryResult {
        public final int[] boundaries;
        public final boolean hasPerfectTransparency;

        public BoundaryResult(int[] boundaries, boolean hasPerfectTransparency) {
            this.boundaries = boundaries;
            this.hasPerfectTransparency = hasPerfectTransparency;
        }
    }

    /**
     * Intelligently detect grid bounds by analyzing content and redistributing edge pixels
     * to prevent cutoff issues that occur with simple division. Now with transparent boundary detection.
     */
    private GridBounds detectGridBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        log.debug("Detecting intelligent grid bounds for {}x{} image", width, height);

        // Step 1: Try to detect transparent pixel boundaries first (new approach)
        BoundaryResult xResult = detectTransparentBoundariesWithQuality(image, true); // vertical boundaries (columns)
        BoundaryResult yResult = detectTransparentBoundariesWithQuality(image, false); // horizontal boundaries (rows)

        int[] xBoundaries = xResult.boundaries;
        int[] yBoundaries = yResult.boundaries;
        boolean xHasPerfectTransparency = xResult.hasPerfectTransparency;
        boolean yHasPerfectTransparency = yResult.hasPerfectTransparency;

        // Step 2: Fall back to content gap detection if transparent boundaries aren't found
        if (xBoundaries == null) {
            xBoundaries = detectContentBoundaries(image, true);
            log.debug("No transparent x-boundaries found, falling back to content detection");
        } else {
            log.debug("Using transparent x boundaries: {} (perfect: {})", 
                    java.util.Arrays.toString(xBoundaries), xHasPerfectTransparency);
        }

        if (yBoundaries == null) {
            yBoundaries = detectContentBoundaries(image, false);
            log.debug("No transparent y-boundaries found, falling back to content detection");
        } else {
            log.debug("Using transparent y boundaries: {} (perfect: {})", 
                    java.util.Arrays.toString(yBoundaries), yHasPerfectTransparency);
        }

        // Step 3: If still no boundaries found, use improved division with edge redistribution
        if (xBoundaries == null) {
            xBoundaries = calculateImprovedDivision(width, 3);
            log.debug("Using improved division for x-axis: {}", java.util.Arrays.toString(xBoundaries));
        }

        if (yBoundaries == null) {
            yBoundaries = calculateImprovedDivision(height, 3);
            log.debug("Using improved division for y-axis: {}", java.util.Arrays.toString(yBoundaries));
        }

        // Step 4: Apply smart buffering to prevent pixel cutoff
        // For perfect transparent boundaries, skip ALL buffering to preserve exact boundaries
        int[] bufferedXBoundaries;
        int[] bufferedYBoundaries;
        
        if (xHasPerfectTransparency) {
            log.debug("SKIPPING all buffering for perfect transparent X boundaries");
            bufferedXBoundaries = xBoundaries; // Use exact boundaries
        } else {
            // Apply normal buffering logic for non-perfect boundaries
            boolean hasHighQualityX = false;
            if (xBoundaries != null && xBoundaries.length == 4) {
                // Check spacing quality for non-perfect boundaries
                int[] xSections = {xBoundaries[1] - xBoundaries[0], xBoundaries[2] - xBoundaries[1], xBoundaries[3] - xBoundaries[2]};
                int idealSection = width / 3;
                boolean xHasGoodSpacing = true;
                for (int section : xSections) {
                    double deviation = Math.abs(section - idealSection) / (double) idealSection;
                    if (deviation > 0.1) {
                        xHasGoodSpacing = false;
                        break;
                    }
                }
                hasHighQualityX = xHasGoodSpacing;
            }
            bufferedXBoundaries = applySmartBuffer(xBoundaries, width, true, hasHighQualityX);
        }
        
        if (yHasPerfectTransparency) {
            log.debug("SKIPPING all buffering for perfect transparent Y boundaries");
            bufferedYBoundaries = yBoundaries; // Use exact boundaries
        } else {
            // Apply normal buffering logic for non-perfect boundaries
            boolean hasHighQualityY = false;
            if (yBoundaries != null && yBoundaries.length == 4) {
                // Check spacing quality for non-perfect boundaries
                int[] ySections = {yBoundaries[1] - yBoundaries[0], yBoundaries[2] - yBoundaries[1], yBoundaries[3] - yBoundaries[2]};
                int idealSection = height / 3;
                boolean yHasGoodSpacing = true;
                for (int section : ySections) {
                    double deviation = Math.abs(section - idealSection) / (double) idealSection;
                    if (deviation > 0.1) {
                        yHasGoodSpacing = false;
                        break;
                    }
                }
                hasHighQualityY = yHasGoodSpacing;
            }
            bufferedYBoundaries = applySmartBuffer(yBoundaries, height, false, hasHighQualityY);
        }

        log.debug("Buffering results: X perfect={}, Y perfect={}", xHasPerfectTransparency, yHasPerfectTransparency);

        // Step 5: Convert boundaries to GridBounds format
        int[][] xBounds = new int[3][2]; // [column][start/end]
        int[][] yBounds = new int[3][2]; // [row][start/end]

        for (int i = 0; i < 3; i++) {
            xBounds[i][0] = bufferedXBoundaries[i];
            xBounds[i][1] = bufferedXBoundaries[i + 1];
            yBounds[i][0] = bufferedYBoundaries[i];
            yBounds[i][1] = bufferedYBoundaries[i + 1];
        }

        // Log final bounds for debugging
        for (int i = 0; i < 3; i++) {
            log.debug("Column {}: x={} to {} (width={}) [buffered]", i, xBounds[i][0], xBounds[i][1], xBounds[i][1] - xBounds[i][0]);
            log.debug("Row {}: y={} to {} (height={}) [buffered]", i, yBounds[i][0], yBounds[i][1], yBounds[i][1] - yBounds[i][0]);
        }

        return new GridBounds(xBounds, yBounds);
    }

    /**
     * Apply smart buffering to grid boundaries to prevent pixel cutoff while avoiding overlaps
     *
     * @param boundaries               Original boundary positions [0, boundary1, boundary2, dimension]
     * @param totalDimension           Total width or height of the image
     * @param isVertical               True for vertical boundaries (columns), false for horizontal (rows)
     * @param hasHighQualityBoundaries True if boundaries were detected using high-quality methods (transparent or good content detection)
     * @return Buffered boundary positions
     */
    private int[] applySmartBuffer(int[] boundaries, int totalDimension, boolean isVertical, boolean hasHighQualityBoundaries) {
        // Calculate adaptive buffer size based on image dimension and boundary quality
        // When we have high-quality boundaries (transparent or good content detection), use NO buffering
        int baseBuffer;
        if (hasHighQualityBoundaries) {
            // High-quality boundaries are already precise, so NO buffering needed to prevent overlap
            baseBuffer = 0;
            log.debug("Using NO buffering (high-quality boundaries detected): base buffer = {}", baseBuffer);
        } else {
            // Standard aggressive buffering for imprecise boundaries
            baseBuffer = Math.max(2, Math.min(8, totalDimension / 150));
            log.debug("Using standard buffering (fallback boundaries): base buffer = {}", baseBuffer);
        }

        // Give extra buffer to the problematic third row (bottom row) to prevent top cutoff
        // But be much more conservative with transparent boundaries
        int[] bufferSizes = new int[4]; // Buffer for each boundary position
        bufferSizes[0] = 0; // First boundary (0) never gets negative buffer
        bufferSizes[1] = baseBuffer; // First internal boundary gets standard buffer
        if (hasHighQualityBoundaries) {
            // Very gentle buffering for precise high-quality boundaries
            bufferSizes[2] = baseBuffer; // Same buffer for both boundaries
        } else {
            // Aggressive buffering for imprecise boundaries (existing behavior)
            bufferSizes[2] = isVertical ? baseBuffer : baseBuffer + 6; // Extra buffer for horizontal
        }
        bufferSizes[3] = 0; // Last boundary (dimension) never exceeds image bounds

        log.debug("Buffer sizes for {}: base={}, buffers=[{}, {}, {}, {}]",
                isVertical ? "vertical" : "horizontal", baseBuffer,
                bufferSizes[0], bufferSizes[1], bufferSizes[2], bufferSizes[3]);

        int[] bufferedBoundaries = new int[4];
        bufferedBoundaries[0] = boundaries[0]; // Always 0
        bufferedBoundaries[3] = boundaries[3]; // Always totalDimension

        // Apply buffers to internal boundaries with overlap prevention
        for (int i = 1; i <= 2; i++) {
            int originalPos = boundaries[i];
            int buffer = bufferSizes[i];

            // For first internal boundary: extend backward (subtract buffer)
            // For second internal boundary: extend backward for horizontal (to help third row)
            int bufferedPos;
            if (i == 1) {
                // Extend first boundary backward to include more content from first section
                bufferedPos = Math.max(bufferedBoundaries[0] + 1, originalPos - buffer);
            } else {
                // For horizontal boundaries (rows), extend second boundary backward to help third row
                // For vertical boundaries (columns), extend forward as usual
                if (!isVertical) {
                    // Extend second boundary backward to include more content in third row
                    bufferedPos = Math.max(bufferedBoundaries[1] + 1, originalPos - buffer);
                } else {
                    // For vertical, extend forward as usual
                    bufferedPos = Math.min(bufferedBoundaries[3] - 1, originalPos + buffer);
                }
            }

            // Ensure boundaries don't cross each other or create sections that are reasonable
            // Use smaller minimum distance to allow more aggressive buffering
            int minDistance = Math.max(15, totalDimension / 30); // Smaller minimum section size for more buffering

            if (i == 1) {
                // First boundary: ensure it's not too close to second boundary
                bufferedPos = Math.min(bufferedPos, boundaries[2] - minDistance);
            } else {
                // Second boundary: ensure it's not too close to first boundary
                bufferedPos = Math.max(bufferedPos, bufferedBoundaries[1] + minDistance);
            }

            bufferedBoundaries[i] = bufferedPos;

            log.debug("Boundary {}: original={}, buffer={}, buffered={}",
                    i, originalPos, buffer, bufferedPos);
        }

        log.debug("Applied smart buffer to {} boundaries: {} -> {} (buffers: {})",
                isVertical ? "vertical" : "horizontal",
                java.util.Arrays.toString(boundaries),
                java.util.Arrays.toString(bufferedBoundaries),
                java.util.Arrays.toString(bufferSizes));

        // Log the effect of buffering on each section
        for (int i = 0; i < 3; i++) {
            int originalSize = boundaries[i + 1] - boundaries[i];
            int bufferedSize = bufferedBoundaries[i + 1] - bufferedBoundaries[i];
            int sizeDiff = bufferedSize - originalSize;
            log.debug("Section {} ({}): {}px -> {}px ({}{}px)",
                    i, isVertical ? "col" : "row", originalSize, bufferedSize,
                    sizeDiff >= 0 ? "+" : "", sizeDiff);
        }

        return bufferedBoundaries;
    }

    /**
     * Detect grid boundaries by looking for lines with mostly transparent pixels
     *
     * @param image    The image to analyze
     * @param vertical If true, detect vertical boundaries (columns), else horizontal (rows)
     * @return Array of 4 boundary positions [0, boundary1, boundary2, dimension] or null if not detected
     */
    private int[] detectTransparentBoundaries(BufferedImage image, boolean vertical) {
        int dimension = vertical ? image.getWidth() : image.getHeight();

        log.debug("Detecting transparent {} boundaries for dimension {}",
                vertical ? "vertical" : "horizontal", dimension);

        // Calculate ideal boundary positions (1/3 and 2/3)
        int idealBoundary1 = dimension / 3;
        int idealBoundary2 = dimension * 2 / 3;
        int idealSectionSize = dimension / 3;

        // Search range around ideal positions (±15% of grid cell size for more flexibility)
        int searchRange = Math.max(8, dimension / 20);

        // Find best transparent boundary near ideal position 1
        int bestBoundary1 = findBestTransparentLine(image, vertical,
                Math.max(1, idealBoundary1 - searchRange),
                Math.min(dimension - 1, idealBoundary1 + searchRange));

        // Find best transparent boundary near ideal position 2
        // Ensure minimum distance from first boundary to avoid overlapping sections
        int minDistanceFromBoundary1 = Math.max(searchRange * 2, idealSectionSize - searchRange);
        int bestBoundary2 = findBestTransparentLine(image, vertical,
                Math.max(bestBoundary1 + minDistanceFromBoundary1, idealBoundary2 - searchRange),
                Math.min(dimension - 1, idealBoundary2 + searchRange));

        // Check if we found good boundaries
        if (bestBoundary1 == -1 || bestBoundary2 == -1 || bestBoundary1 >= bestBoundary2) {
            log.debug("Could not find suitable transparent {} boundaries", vertical ? "vertical" : "horizontal");
            return null;
        }

        // Validate boundary quality by checking transparency percentage and spacing
        double transparencyThreshold = 0.9; // At least 90% transparent pixels for high confidence
        double boundary1Transparency = calculateLineTransparency(image, vertical, bestBoundary1);
        double boundary2Transparency = calculateLineTransparency(image, vertical, bestBoundary2);

        // Check if we found perfect transparent lines (100% transparency)
        boolean hasPerfectTransparency = boundary1Transparency >= 1.0 && boundary2Transparency >= 1.0;

        // Check spacing - boundaries should divide the image into roughly equal thirds
        int section1Size = bestBoundary1 - 0;
        int section2Size = bestBoundary2 - bestBoundary1;
        int section3Size = dimension - bestBoundary2;

        // Calculate how much each section deviates from ideal size
        double maxDeviationRatio = hasPerfectTransparency ? 0.25 : 0.05; // More lenient for perfect boundaries

        double section1Deviation = Math.abs(section1Size - idealSectionSize) / (double) idealSectionSize;
        double section2Deviation = Math.abs(section2Size - idealSectionSize) / (double) idealSectionSize;
        double section3Deviation = Math.abs(section3Size - idealSectionSize) / (double) idealSectionSize;

        boolean spacingIsGood = section1Deviation <= maxDeviationRatio &&
                section2Deviation <= maxDeviationRatio &&
                section3Deviation <= maxDeviationRatio;

        // For non-perfect boundaries, enforce strict transparency threshold
        if (!hasPerfectTransparency && (boundary1Transparency < transparencyThreshold || boundary2Transparency < transparencyThreshold)) {
            log.debug("Transparent {} boundaries don't meet quality threshold: {}%, {}% (need {}%)",
                    vertical ? "vertical" : "horizontal",
                    String.format("%.1f", boundary1Transparency * 100),
                    String.format("%.1f", boundary2Transparency * 100),
                    String.format("%.1f", transparencyThreshold * 100));
            return null;
        }

        // Apply spacing validation with different tolerances
        if (!spacingIsGood) {
            String spacingType = hasPerfectTransparency ? "perfect transparency (relaxed spacing)" : "fallback boundaries";
            log.debug("Transparent {} boundaries ({}) have poor spacing: sections=[{}, {}, {}] (ideal={}), deviations=[{}%, {}%, {}%] (max={}%)",
                    vertical ? "vertical" : "horizontal", spacingType,
                    section1Size, section2Size, section3Size, idealSectionSize,
                    String.format("%.1f", section1Deviation * 100),
                    String.format("%.1f", section2Deviation * 100),
                    String.format("%.1f", section3Deviation * 100),
                    String.format("%.1f", maxDeviationRatio * 100));
            return null;
        }

        log.debug("Found excellent transparent {} boundaries at {} ({}% transparent) and {} ({}% transparent) with good spacing: sections=[{}, {}, {}]",
                vertical ? "vertical" : "horizontal",
                bestBoundary1, String.format("%.1f", boundary1Transparency * 100),
                bestBoundary2, String.format("%.1f", boundary2Transparency * 100),
                section1Size, section2Size, section3Size);

        return new int[]{0, bestBoundary1, bestBoundary2, dimension};
    }

    /**
     * Detect transparent boundaries and track whether they came from perfect transparency
     */
    private BoundaryResult detectTransparentBoundariesWithQuality(BufferedImage image, boolean vertical) {
        int[] boundaries = detectTransparentBoundaries(image, vertical);
        
        if (boundaries == null) {
            return new BoundaryResult(null, false);
        }
        
        // Check if both boundaries were found with perfect transparency (100% alpha=0)
        boolean hasPerfectTransparency = false;
        
        if (boundaries.length == 4) {
            boolean boundary1Perfect = isPerfectlyTransparentLine(image, vertical, boundaries[1]);
            boolean boundary2Perfect = isPerfectlyTransparentLine(image, vertical, boundaries[2]);
            
            hasPerfectTransparency = boundary1Perfect && boundary2Perfect;
            
            log.debug("Transparent {} boundaries perfect check: boundary1={}, boundary2={}, both perfect={}",
                    vertical ? "vertical" : "horizontal",
                    boundary1Perfect ? "PERFECT" : "partial",
                    boundary2Perfect ? "PERFECT" : "partial",
                    hasPerfectTransparency);
        }
        
        return new BoundaryResult(boundaries, hasPerfectTransparency);
    }

    /**
     * Check if a line is perfectly transparent (all pixels have alpha = 0)
     */
    private boolean isPerfectlyTransparentLine(BufferedImage image, boolean vertical, int position) {
        int dimension = vertical ? image.getHeight() : image.getWidth();
        
        for (int i = 0; i < dimension; i++) {
            int rgb = vertical ? image.getRGB(position, i) : image.getRGB(i, position);
            int alpha = (rgb >> 24) & 0xFF;
            
            // If any pixel is not completely transparent, this line is not perfect
            if (alpha != 0) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Find the line with the highest transparency percentage within a given range
     * First tries to find 100% transparent lines, then falls back to 95% threshold
     *
     * @param image    The image to analyze
     * @param vertical If true, analyze vertical lines, else horizontal
     * @param startPos Start position of search range
     * @param endPos   End position of search range
     * @return Position of best transparent line, or -1 if none found
     */
    private int findBestTransparentLine(BufferedImage image, boolean vertical, int startPos, int endPos) {
        // First pass: Look for 100% transparent lines (perfect transparency)
        int bestPosition = findPerfectTransparentLine(image, vertical, startPos, endPos);
        
        if (bestPosition != -1) {
            log.debug("Found perfect transparent line at position {} with 100% transparency", bestPosition);
            return bestPosition;
        }
        
        // Second pass: Fall back to 95% transparency threshold
        log.debug("No 100% transparent lines found, falling back to 95% threshold");
        bestPosition = -1;
        double bestTransparency = 0.0;
        double minTransparency = 0.95; // Minimum 95% transparency to consider

        for (int pos = startPos; pos <= endPos; pos++) {
            double transparency = calculateLineTransparency(image, vertical, pos);

            if (transparency >= minTransparency && transparency > bestTransparency) {
                bestTransparency = transparency;
                bestPosition = pos;
            }
        }

        if (bestPosition != -1) {
            log.debug("Best transparent line at position {} with {}% transparency (fallback)",
                    bestPosition, String.format("%.1f", bestTransparency * 100));
        }

        return bestPosition;
    }

    /**
     * Find a line with perfect transparency (100% transparent - all pixels have alpha = 0)
     *
     * @param image    The image to analyze
     * @param vertical If true, analyze vertical lines, else horizontal
     * @param startPos Start position of search range
     * @param endPos   End position of search range
     * @return Position of perfectly transparent line, or -1 if none found
     */
    private int findPerfectTransparentLine(BufferedImage image, boolean vertical, int startPos, int endPos) {
        int dimension = vertical ? image.getHeight() : image.getWidth();
        
        for (int pos = startPos; pos <= endPos; pos++) {
            boolean isPerfectlyTransparent = true;
            
            // Check every pixel in this line
            for (int i = 0; i < dimension; i++) {
                int rgb = vertical ? image.getRGB(pos, i) : image.getRGB(i, pos);
                int alpha = (rgb >> 24) & 0xFF;
                
                // If any pixel is not completely transparent, this line is not perfect
                if (alpha != 0) {
                    isPerfectlyTransparent = false;
                    break;
                }
            }
            
            if (isPerfectlyTransparent) {
                log.debug("Found perfectly transparent {} line at position {}", 
                        vertical ? "vertical" : "horizontal", pos);
                return pos;
            }
        }
        
        log.debug("No perfectly transparent {} lines found in range [{}, {}]", 
                vertical ? "vertical" : "horizontal", startPos, endPos);
        return -1;
    }

    /**
     * Calculate the percentage of transparent pixels along a line
     *
     * @param image    The image to analyze
     * @param vertical If true, analyze vertical line, else horizontal
     * @param position The line position to analyze
     * @return Percentage of transparent pixels (0.0 to 1.0)
     */
    private double calculateLineTransparency(BufferedImage image, boolean vertical, int position) {
        int dimension = vertical ? image.getHeight() : image.getWidth();
        int transparentPixels = 0;
        int totalPixels = dimension;

        for (int i = 0; i < dimension; i++) {
            int rgb = vertical ? image.getRGB(position, i) : image.getRGB(i, position);
            int alpha = (rgb >> 24) & 0xFF;

            // Consider pixels with very low alpha as transparent
            // Also consider very light pixels (near white) as background
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;

            boolean isTransparent = alpha < 20; // Very low alpha
            boolean isNearWhite = red > 240 && green > 240 && blue > 240 && alpha > 200; // Near white

            if (isTransparent || isNearWhite) {
                transparentPixels++;
            }
        }

        return (double) transparentPixels / totalPixels;
    }

    /**
     * Detect natural content boundaries in the image by analyzing pixel patterns
     *
     * @param image    The image to analyze
     * @param vertical If true, detect vertical boundaries (columns), else horizontal (rows)
     * @return Array of 4 boundary positions [0, boundary1, boundary2, dimension] or null if not detected
     */
    private int[] detectContentBoundaries(BufferedImage image, boolean vertical) {
        int dimension = vertical ? image.getWidth() : image.getHeight();
        int perpDimension = vertical ? image.getHeight() : image.getWidth();

        log.debug("Analyzing {} boundaries: dimension={}, perpDimension={}",
                vertical ? "vertical" : "horizontal", dimension, perpDimension);

        // Analyze pixel variation along the dimension to find low-content areas (gaps)
        double[] contentDensity = new double[dimension];

        for (int pos = 0; pos < dimension; pos++) {
            int contentPixels = 0;

            // Sample along the perpendicular dimension
            for (int perpPos = 0; perpPos < perpDimension; perpPos++) {
                int rgb = vertical ? image.getRGB(pos, perpPos) : image.getRGB(perpPos, pos);

                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                // Count non-background pixels
                boolean isTransparent = alpha < 50;
                boolean isWhite = red > 240 && green > 240 && blue > 240;
                boolean isBackground = isTransparent || isWhite;

                if (!isBackground) {
                    contentPixels++;
                }
            }

            contentDensity[pos] = (double) contentPixels / perpDimension;
        }

        // Find the two lowest density regions that could serve as boundaries
        List<Integer> gapCandidates = new ArrayList<>();

        // Use stricter threshold for better gap detection
        double threshold = 0.05; // Consider positions with <5% content as potential gaps

        for (int pos = 1; pos < dimension - 1; pos++) {
            if (contentDensity[pos] < threshold) {
                gapCandidates.add(pos);
            }
        }

        // If no gaps found with strict threshold, try relaxed threshold but only in specific ranges
        if (gapCandidates.size() < 2) {
            log.debug("No strict gaps found for {}, trying relaxed threshold in target ranges",
                    vertical ? "vertical" : "horizontal");

            double relaxedThreshold = 0.15; // 15% content threshold for relaxed search
            int targetBoundary1 = dimension / 3;
            int targetBoundary2 = dimension * 2 / 3;
            int searchRange = dimension / 10; // Search within 10% of target positions

            // Search around ideal boundary 1
            for (int pos = Math.max(1, targetBoundary1 - searchRange);
                 pos < Math.min(dimension - 1, targetBoundary1 + searchRange); pos++) {
                if (contentDensity[pos] < relaxedThreshold) {
                    gapCandidates.add(pos);
                }
            }

            // Search around ideal boundary 2
            for (int pos = Math.max(1, targetBoundary2 - searchRange);
                 pos < Math.min(dimension - 1, targetBoundary2 + searchRange); pos++) {
                if (contentDensity[pos] < relaxedThreshold && !gapCandidates.contains(pos)) {
                    gapCandidates.add(pos);
                }
            }
        }

        if (gapCandidates.size() < 2) {
            log.debug("Could not detect natural {} boundaries - insufficient gap candidates ({})",
                    vertical ? "vertical" : "horizontal", gapCandidates.size());
            return null;
        }

        // Find two boundaries that divide the image into roughly three equal parts
        int targetBoundary1 = dimension / 3;
        int targetBoundary2 = dimension * 2 / 3;
        int idealSectionSize = dimension / 3;

        // Sort candidates by their content density (lowest first - better gaps)
        gapCandidates.sort((a, b) -> Double.compare(contentDensity[a], contentDensity[b]));

        int bestBoundary1 = -1;
        int bestBoundary2 = -1;
        double bestScore = Double.MAX_VALUE;

        // Try different combinations of boundaries to find the best spacing
        for (int i = 0; i < gapCandidates.size() - 1; i++) {
            for (int j = i + 1; j < gapCandidates.size(); j++) {
                int candidate1 = gapCandidates.get(i);
                int candidate2 = gapCandidates.get(j);

                // Ensure candidate1 < candidate2
                if (candidate1 > candidate2) {
                    int temp = candidate1;
                    candidate1 = candidate2;
                    candidate2 = temp;
                }

                // Check spacing quality
                int section1Size = candidate1 - 0;
                int section2Size = candidate2 - candidate1;
                int section3Size = dimension - candidate2;

                // Calculate deviations from ideal spacing
                double dev1 = Math.abs(section1Size - idealSectionSize) / (double) idealSectionSize;
                double dev2 = Math.abs(section2Size - idealSectionSize) / (double) idealSectionSize;
                double dev3 = Math.abs(section3Size - idealSectionSize) / (double) idealSectionSize;

                // Combine spacing score with content density score
                double spacingScore = (dev1 + dev2 + dev3) / 3.0; // Average deviation
                double contentScore = (contentDensity[candidate1] + contentDensity[candidate2]) / 2.0; // Lower is better
                double totalScore = spacingScore * 0.7 + contentScore * 0.3; // Weight spacing more heavily

                if (totalScore < bestScore && dev1 < 0.25 && dev2 < 0.25 && dev3 < 0.25) { // Max 25% deviation per section
                    bestScore = totalScore;
                    bestBoundary1 = candidate1;
                    bestBoundary2 = candidate2;
                }
            }
        }

        // Ensure boundaries are reasonable
        if (bestBoundary1 == -1 || bestBoundary2 == -1 || bestBoundary1 >= bestBoundary2) {
            log.debug("Could not find good {} boundaries with acceptable spacing from {} candidates",
                    vertical ? "vertical" : "horizontal", gapCandidates.size());
            return null;
        }

        // Log the results
        int section1Size = bestBoundary1 - 0;
        int section2Size = bestBoundary2 - bestBoundary1;
        int section3Size = dimension - bestBoundary2;

        log.debug("Detected natural {} boundaries at {} (content: {}%) and {} (content: {}%) for dimension {} with sections=[{}, {}, {}]",
                vertical ? "vertical" : "horizontal",
                bestBoundary1, String.format("%.1f", contentDensity[bestBoundary1] * 100),
                bestBoundary2, String.format("%.1f", contentDensity[bestBoundary2] * 100),
                dimension, section1Size, section2Size, section3Size);

        return new int[]{0, bestBoundary1, bestBoundary2, dimension};
    }

    /**
     * Calculate improved division that redistributes remainder pixels to prevent cutoff
     *
     * @param totalSize The total dimension to divide
     * @param divisions Number of divisions (3 for 3x3 grid)
     * @return Array of boundary positions [0, boundary1, boundary2, totalSize]
     */
    private int[] calculateImprovedDivision(int totalSize, int divisions) {
        int baseSize = totalSize / divisions;
        int remainder = totalSize % divisions;

        log.debug("Calculating improved division: {} pixels / {} divisions = {} base + {} remainder",
                totalSize, divisions, baseSize, remainder);

        int[] boundaries = new int[divisions + 1];
        boundaries[0] = 0;

        for (int i = 1; i <= divisions; i++) {
            // Distribute remainder pixels across the first 'remainder' sections
            int extraPixel = (i <= remainder) ? 1 : 0;
            boundaries[i] = boundaries[i - 1] + baseSize + extraPixel;
        }

        // Ensure the last boundary matches total size exactly
        boundaries[divisions] = totalSize;

        return boundaries;
    }

    private List<String> cropGrid3x3(BufferedImage originalImage, boolean centerIcons, int targetSize) throws IOException {
        List<String> icons = new ArrayList<>();

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        log.debug("Starting intelligent 3x3 grid cropping for image {}x{}", width, height);

        GridBounds gridBounds = detectGridBounds(originalImage);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                Rectangle iconRect = gridBounds.getIconRectangle(row, col);

                log.debug("Cropping icon at position [{},{}] with bounds: x={}, y={}, width={}, height={}",
                        row, col, iconRect.x, iconRect.y, iconRect.width, iconRect.height);

                BufferedImage croppedIcon = originalImage.getSubimage(
                        iconRect.x, iconRect.y, iconRect.width, iconRect.height);

                // Optionally center the icon
                if (centerIcons) {
                    int size = targetSize > 0 ? targetSize : Math.max(iconRect.width, iconRect.height);
                    croppedIcon = centerIcon(croppedIcon, size);
                    log.debug("Centered icon at position [{},{}] to size {}x{}", row, col, size, size);
                } else {
                    log.debug("Cropped icon at position [{},{}] with size {}x{}",
                            row, col, iconRect.width, iconRect.height);
                }

                String base64Icon = bufferedImageToBase64(croppedIcon);
                icons.add(base64Icon);
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
