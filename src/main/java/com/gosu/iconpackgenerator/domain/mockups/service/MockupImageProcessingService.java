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
    private static final int WHITE_THRESHOLD = 245;
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
     * Extract connected foreground regions (UI components) from a mockup rendered on a white background.
     * Uses a simple flood-fill based content bound detection to locate components and crops them out.
     *
     * @param mockupImage Parsed mockup image
     * @return List of cropped component images
     */
    public List<BufferedImage> extractComponentsFromMockup(BufferedImage mockupImage) {
        if (mockupImage == null) {
            throw new IllegalArgumentException("Mockup image must not be null");
        }

        int width = mockupImage.getWidth();
        int height = mockupImage.getHeight();
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
                int rgb = mockupImage.getRGB(x, y);
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
                        int neighborRgb = mockupImage.getRGB(nx, ny);
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
                    graphics.drawImage(mockupImage,
                            0, 0, cropWidth, cropHeight,
                            cropX, cropY, cropX + cropWidth, cropY + cropHeight,
                            null);
                } finally {
                    graphics.dispose();
                }
                BufferedImage transparentComponent = removeWhiteBackground(croppedComponent);
                components.add(resizeComponentIfNeeded(transparentComponent));
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
        if (alpha < 16) {
            return false;
        }

        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int brightness = (red + green + blue) / 3;
        return brightness < WHITE_THRESHOLD;
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

    private BufferedImage removeWhiteBackground(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage argbImage;
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            argbImage = image;
        } else {
            argbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = argbImage.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }

        int[] backgroundColor = estimateBackgroundColor(argbImage);
        boolean[] visited = new boolean[width * height];
        Deque<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            enqueueIfBackground(queue, visited, argbImage, x, 0, width, height, backgroundColor);
            enqueueIfBackground(queue, visited, argbImage, x, height - 1, width, height, backgroundColor);
        }
        for (int y = 0; y < height; y++) {
            enqueueIfBackground(queue, visited, argbImage, 0, y, width, height, backgroundColor);
            enqueueIfBackground(queue, visited, argbImage, width - 1, y, width, height, backgroundColor);
        }

        int[] dirs = {-1, 0, 1, 0, -1};
        while (!queue.isEmpty()) {
            int[] point = queue.removeFirst();
            int px = point[0];
            int py = point[1];
            argbImage.setRGB(px, py, 0x00FFFFFF);

            for (int i = 0; i < 4; i++) {
                int nx = px + dirs[i];
                int ny = py + dirs[i + 1];
                enqueueIfBackground(queue, visited, argbImage, nx, ny, width, height, backgroundColor);
            }
        }

        if (backgroundColor != null) {
            int iterations = 0;
            boolean changed;
            do {
                changed = false;
                boolean[] toClear = new boolean[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int rgb = argbImage.getRGB(x, y);
                        if (!isBackgroundLike(rgb, backgroundColor)) {
                            continue;
                        }
                        if (hasTransparentNeighbor(argbImage, x, y)) {
                            toClear[y * width + x] = true;
                            changed = true;
                        }
                    }
                }
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        if (toClear[y * width + x]) {
                            argbImage.setRGB(x, y, 0x00FFFFFF);
                        }
                    }
                }
                iterations++;
            } while (changed && iterations < 6);
        }

        return argbImage;
    }

    private void enqueueIfBackground(Deque<int[]> queue, boolean[] visited, BufferedImage image,
                                     int x, int y, int width, int height, int[] backgroundColor) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        int index = y * width + x;
        if (visited[index]) {
            return;
        }
        visited[index] = true;

        int rgb = image.getRGB(x, y);
        if (!isBackgroundPixel(rgb, backgroundColor)) {
            return;
        }
        queue.add(new int[]{x, y});
    }

    private boolean isBackgroundPixel(int rgb, int[] backgroundColor) {
        int alpha = (rgb >>> 24) & 0xFF;
        if (alpha < 10) {
            return true;
        }
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int brightness = (red + green + blue) / 3;
        if (backgroundColor != null) {
            int distance = colorDistance(red, green, blue, backgroundColor);
            int backgroundBrightness = (backgroundColor[0] + backgroundColor[1] + backgroundColor[2]) / 3;
            if (brightness >= backgroundBrightness - 25 && distance <= 70) {
                return true;
            }
        }
        return brightness >= WHITE_THRESHOLD;
    }

    private boolean isBackgroundLike(int rgb, int[] backgroundColor) {
        if (backgroundColor == null) {
            return false;
        }
        int alpha = (rgb >>> 24) & 0xFF;
        if (alpha < 10) {
            return false;
        }
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int brightness = (red + green + blue) / 3;
        int backgroundBrightness = (backgroundColor[0] + backgroundColor[1] + backgroundColor[2]) / 3;
        int distance = colorDistance(red, green, blue, backgroundColor);
        return brightness >= backgroundBrightness - 20 && distance <= 60;
    }

    private boolean hasTransparentNeighbor(BufferedImage image, int x, int y) {
        int width = image.getWidth();
        int height = image.getHeight();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                    continue;
                }
                int neighbor = image.getRGB(nx, ny);
                int alpha = (neighbor >>> 24) & 0xFF;
                if (alpha < 10) {
                    return true;
                }
            }
        }
        return false;
    }

    private int[] estimateBackgroundColor(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        int count = 0;

        for (int x = 0; x < width; x++) {
            int[] top = extractIfBright(image.getRGB(x, 0));
            if (top != null) {
                sumR += top[0];
                sumG += top[1];
                sumB += top[2];
                count++;
            }
            int[] bottom = extractIfBright(image.getRGB(x, height - 1));
            if (bottom != null) {
                sumR += bottom[0];
                sumG += bottom[1];
                sumB += bottom[2];
                count++;
            }
        }
        for (int y = 0; y < height; y++) {
            int[] left = extractIfBright(image.getRGB(0, y));
            if (left != null) {
                sumR += left[0];
                sumG += left[1];
                sumB += left[2];
                count++;
            }
            int[] right = extractIfBright(image.getRGB(width - 1, y));
            if (right != null) {
                sumR += right[0];
                sumG += right[1];
                sumB += right[2];
                count++;
            }
        }

        if (count == 0) {
            return null;
        }

        return new int[]{
                (int) Math.min(255, Math.max(0, sumR / count)),
                (int) Math.min(255, Math.max(0, sumG / count)),
                (int) Math.min(255, Math.max(0, sumB / count))
        };
    }

    private int[] extractIfBright(int rgb) {
        int alpha = (rgb >>> 24) & 0xFF;
        if (alpha < 10) {
            return null;
        }
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int brightness = (red + green + blue) / 3;
        if (brightness < 200) {
            return null;
        }
        return new int[]{red, green, blue};
    }

    private int colorDistance(int red, int green, int blue, int[] backgroundColor) {
        return Math.abs(red - backgroundColor[0])
                + Math.abs(green - backgroundColor[1])
                + Math.abs(blue - backgroundColor[2]);
    }
}
