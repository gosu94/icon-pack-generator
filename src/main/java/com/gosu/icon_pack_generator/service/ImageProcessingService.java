package com.gosu.icon_pack_generator.service;

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
import java.util.List;
import java.util.Iterator;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingService {
    
    private final BackgroundRemovalService backgroundRemovalService;
    
    /**
     * Crop a 3x3 grid of icons from the generated image
     * @param imageData The original image as byte array
     * @param iconCount The number of icons to extract (9 or 18)
     * @return List of cropped icon images as base64 strings
     */
    public List<String> cropIconsFromGrid(byte[] imageData, int iconCount) {
        return cropIconsFromGrid(imageData, iconCount, true, 0, false);
    }
    
    /**
     * Crop a 3x3 grid of icons from the generated image with optional centering
     * @param imageData The original image as byte array
     * @param iconCount The number of icons to extract (9 or 18)
     * @param centerIcons Whether to center the icons on their canvas
     * @param targetSize Target size for centered icons (0 = auto-size based on original)
     * @return List of cropped icon images as base64 strings
     */
    public List<String> cropIconsFromGrid(byte[] imageData, int iconCount, boolean centerIcons, int targetSize) {
        return cropIconsFromGrid(imageData, iconCount, centerIcons, targetSize, false);
    }
    
    /**
     * Crop a 3x3 grid of icons from the generated image with optional centering and background removal
     * @param imageData The original image as byte array
     * @param iconCount The number of icons to extract (9 or 18)
     * @param centerIcons Whether to center the icons on their canvas
     * @param targetSize Target size for centered icons (0 = auto-size based on original)
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
            // During generation, background removal is typically disabled to preserve content bounds
            // During export, background removal may be enabled per user preference
            byte[] processedImageData = imageData;
            if (removeBackground) {
                log.info("Removing background from grid image before cropping icons");
                processedImageData = backgroundRemovalService.removeBackground(imageData);
                
                if (processedImageData.length != imageData.length) {
                    log.info("Background removal changed image size from {} to {} bytes", 
                            imageData.length, processedImageData.length);
                }
            } else {
                log.debug("Background removal disabled - preserving original image for better content bounds detection");
            }
            
            BufferedImage originalImage = null;
            
            // Check if it's WebP format for better logging
            if (isWebPFormat(processedImageData)) {
                log.info("WebP format detected. Attempting to decode using TwelveMonkeys ImageIO WebP support...");
                originalImage = readImageWithWebPSupport(processedImageData);
            } else {
                log.debug("Standard image format detected, using regular ImageIO...");
                originalImage = ImageIO.read(new ByteArrayInputStream(processedImageData));
            }
            
            if (originalImage == null) {
                log.error("Failed to parse image data - ImageIO.read() returned null. Data size: {} bytes", processedImageData.length);
                // Log first few bytes to help debug
                if (processedImageData.length > 10) {
                    byte[] firstBytes = java.util.Arrays.copyOf(processedImageData, 10);
                    log.error("First 10 bytes of image data: {}", java.util.Arrays.toString(firstBytes));
                }
                throw new RuntimeException("Failed to parse image data - ImageIO returned null");
            }
            
            log.info("Successfully parsed image: {}x{} pixels", originalImage.getWidth(), originalImage.getHeight());
            
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
            
            log.info("Successfully cropped {} icons from grid", croppedIcons.size());
            return croppedIcons;
            
        } catch (IOException e) {
            log.error("Error processing image", e);
            throw new RuntimeException("Failed to process image", e);
        }
    }
    
    /**
     * Center an icon by detecting its content bounding box and placing it centered on a square canvas
     * @param iconImage The original icon image
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
     * @param iconImage The original icon image
     * @param targetSize The size of the square canvas (if 0, uses the original image's larger dimension)
     * @return The centered icon as a base64 string
     */
    public String centerIconToBase64(BufferedImage iconImage, int targetSize) throws IOException {
        BufferedImage centeredIcon = centerIcon(iconImage, targetSize);
        return bufferedImageToBase64(centeredIcon);
    }
    
    /**
     * Remove background from an individual icon's base64 data
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
                log.info("Image appears to be mostly black. Treating black pixels as content for centering.");
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
        log.info("Image diagnostics: {}x{} pixels, {} bytes original file size", width, height, originalFileSize);
        log.info("Pixel sample analysis ({}): {}% transparent, {}% white, {}% black, {}% colored", 
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
        
        // Check if dimensions are suitable for 3x3 grid
        if (width % 3 != 0 || height % 3 != 0) {
            log.warn("Image dimensions ({}x{}) are not perfectly divisible by 3 - icon cropping may have edge artifacts", width, height);
        }
    }
    
    /**
     * Create a new square canvas and center the given image on it
     * @param contentImage The image to center
     * @param canvasSize The size of the square canvas
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
     * Intelligently detect grid bounds by analyzing content and redistributing edge pixels
     * to prevent cutoff issues that occur with simple division
     */
    private GridBounds detectGridBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        log.debug("Detecting intelligent grid bounds for {}x{} image", width, height);
        
        // Step 1: Try to detect natural boundaries by analyzing content gaps
        int[] xBoundaries = detectContentBoundaries(image, true); // vertical boundaries (columns)
        int[] yBoundaries = detectContentBoundaries(image, false); // horizontal boundaries (rows)
        
        // Step 2: If natural boundaries aren't found, use improved division with edge redistribution
        if (xBoundaries == null) {
            xBoundaries = calculateImprovedDivision(width, 3);
            log.debug("Using improved division for x-axis: {}", java.util.Arrays.toString(xBoundaries));
        } else {
            log.debug("Using content-detected x boundaries: {}", java.util.Arrays.toString(xBoundaries));
        }
        
        if (yBoundaries == null) {
            yBoundaries = calculateImprovedDivision(height, 3);
            log.debug("Using improved division for y-axis: {}", java.util.Arrays.toString(yBoundaries));
        } else {
            log.debug("Using content-detected y boundaries: {}", java.util.Arrays.toString(yBoundaries));
        }
        
        // Step 3: Apply smart buffering to prevent pixel cutoff
        int[] bufferedXBoundaries = applySmartBuffer(xBoundaries, width, true);
        int[] bufferedYBoundaries = applySmartBuffer(yBoundaries, height, false);
        
        // Step 4: Convert boundaries to GridBounds format
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
     * @param boundaries Original boundary positions [0, boundary1, boundary2, dimension]
     * @param totalDimension Total width or height of the image
     * @param isVertical True for vertical boundaries (columns), false for horizontal (rows)
     * @return Buffered boundary positions
     */
    private int[] applySmartBuffer(int[] boundaries, int totalDimension, boolean isVertical) {
        // Calculate adaptive buffer size based on image dimension
        // Larger images get slightly larger buffers, but cap it to prevent excessive overlap
        int baseBuffer = Math.max(2, Math.min(8, totalDimension / 150));
        
        // Give extra buffer to the problematic third row (bottom row) to prevent top cutoff
        int[] bufferSizes = new int[4]; // Buffer for each boundary position
        bufferSizes[0] = 0; // First boundary (0) never gets negative buffer
        bufferSizes[1] = baseBuffer; // First internal boundary gets standard buffer
        bufferSizes[2] = isVertical ? baseBuffer : baseBuffer + 6; // Second internal boundary gets much larger buffer for horizontal (rows)
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
     * Detect natural content boundaries in the image by analyzing pixel patterns
     * @param image The image to analyze
     * @param vertical If true, detect vertical boundaries (columns), else horizontal (rows)
     * @return Array of 4 boundary positions [0, boundary1, boundary2, dimension] or null if not detected
     */
    private int[] detectContentBoundaries(BufferedImage image, boolean vertical) {
        int dimension = vertical ? image.getWidth() : image.getHeight();
        int perpDimension = vertical ? image.getHeight() : image.getWidth();
        
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
        double threshold = 0.1; // Consider positions with <10% content as potential gaps
        
        for (int pos = 1; pos < dimension - 1; pos++) {
            if (contentDensity[pos] < threshold) {
                gapCandidates.add(pos);
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
        
        int bestBoundary1 = gapCandidates.get(0);
        int bestBoundary2 = gapCandidates.get(gapCandidates.size() - 1);
        
        for (int candidate : gapCandidates) {
            if (Math.abs(candidate - targetBoundary1) < Math.abs(bestBoundary1 - targetBoundary1)) {
                bestBoundary1 = candidate;
            }
            if (Math.abs(candidate - targetBoundary2) < Math.abs(bestBoundary2 - targetBoundary2)) {
                bestBoundary2 = candidate;
            }
        }
        
        // Ensure boundaries are in order and reasonable
        if (bestBoundary1 >= bestBoundary2 || bestBoundary1 < dimension * 0.2 || bestBoundary2 > dimension * 0.8) {
            log.debug("Detected {} boundaries are not reasonable: {} and {} for dimension {}", 
                     vertical ? "vertical" : "horizontal", bestBoundary1, bestBoundary2, dimension);
            return null;
        }
        
        log.debug("Detected natural {} boundaries at {} and {} for dimension {}", 
                 vertical ? "vertical" : "horizontal", bestBoundary1, bestBoundary2, dimension);
        
        return new int[]{0, bestBoundary1, bestBoundary2, dimension};
    }
    
    /**
     * Calculate improved division that redistributes remainder pixels to prevent cutoff
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
        
        log.info("Starting intelligent 3x3 grid cropping for image {}x{}", width, height);
        
        // Use intelligent grid detection instead of simple division
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
