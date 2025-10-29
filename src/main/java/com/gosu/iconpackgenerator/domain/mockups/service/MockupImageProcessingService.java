package com.gosu.iconpackgenerator.domain.mockups.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockupImageProcessingService {

    public static final int MOCKUP_TARGET_WIDTH = 1920; // Full HD width for 16:9 mockups
    private static final int FOREGROUND_ALPHA_THRESHOLD = 16;
    private static final int MIN_COMPONENT_AREA = 200;
    private static final int COMPONENT_MARGIN = 3;
    private static final int MIN_COMPONENT_DIMENSION = 256;

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
     * Extract connected foreground regions (UI components) from a mockup rendered on a transparent background.
     * Uses an alpha-based flood-fill to locate components and crops them out.
     *
     * @param mockupImage Parsed mockup image
     * @return List of cropped component images
     */
    public List<BufferedImage> extractComponentsFromMockup(BufferedImage mockupImage) {
        if (mockupImage == null) {
            throw new IllegalArgumentException("Mockup image must not be null");
        }

        BufferedImage sourceImage = ensureArgbImage(mockupImage);
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        boolean[] visited = new boolean[width * height];
        List<BufferedImage> components = new ArrayList<>();
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (visited[index]) {
                    continue;
                }

                visited[index] = true;
                int rgb = sourceImage.getRGB(x, y);
                if (!isForegroundPixel(rgb)) {
                    continue;
                }

                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                int minX = x;
                int minY = y;
                int maxX = x;
                int maxY = y;
                int pixelCount = 0;

                while (!queue.isEmpty()) {
                    int[] point = queue.removeFirst();
                    int cx = point[0];
                    int cy = point[1];
                    pixelCount++;

                    minX = Math.min(minX, cx);
                    minY = Math.min(minY, cy);
                    maxX = Math.max(maxX, cx);
                    maxY = Math.max(maxY, cy);

                    for (int dir = 0; dir < dx.length; dir++) {
                        int nx = cx + dx[dir];
                        int ny = cy + dy[dir];
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                            continue;
                        }

                        int neighborIndex = ny * width + nx;
                        if (visited[neighborIndex]) {
                            continue;
                        }

                        visited[neighborIndex] = true;
                        int neighborRgb = sourceImage.getRGB(nx, ny);
                        if (!isForegroundPixel(neighborRgb)) {
                            continue;
                        }

                        queue.add(new int[]{nx, ny});
                    }
                }

                int componentWidth = maxX - minX + 1;
                int componentHeight = maxY - minY + 1;
                int area = componentWidth * componentHeight;
                if (area < MIN_COMPONENT_AREA || pixelCount < MIN_COMPONENT_AREA / 4) {
                    continue;
                }

                int cropX = Math.max(0, minX - COMPONENT_MARGIN);
                int cropY = Math.max(0, minY - COMPONENT_MARGIN);
                int cropWidth = Math.min(width - cropX, componentWidth + COMPONENT_MARGIN * 2);
                int cropHeight = Math.min(height - cropY, componentHeight + COMPONENT_MARGIN * 2);

                BufferedImage croppedComponent = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = croppedComponent.createGraphics();
                try {
                    graphics.drawImage(sourceImage,
                            0, 0, cropWidth, cropHeight,
                            cropX, cropY, cropX + cropWidth, cropY + cropHeight,
                            null);
                } finally {
                    graphics.dispose();
                }
                components.add(resizeComponentIfNeeded(croppedComponent));
            }
        }

        return components;
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

    private boolean isForegroundPixel(int rgb) {
        int alpha = (rgb >>> 24) & 0xFF;
        return alpha >= FOREGROUND_ALPHA_THRESHOLD;
    }

    private BufferedImage resizeComponentIfNeeded(BufferedImage component) {
        int originalWidth = component.getWidth();
        int originalHeight = component.getHeight();
        int minDimension = Math.min(originalWidth, originalHeight);

        if (minDimension >= MIN_COMPONENT_DIMENSION) {
            return component;
        }

        double scale = (double) MIN_COMPONENT_DIMENSION / minDimension;
        int targetWidth = (int) Math.round(originalWidth * scale);
        int targetHeight = (int) Math.round(originalHeight * scale);

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(component, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }
        return resized;
    }

    private BufferedImage ensureArgbImage(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }

        BufferedImage argbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = argbImage.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return argbImage;
    }
}
