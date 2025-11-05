package com.gosu.iconpackgenerator.domain.icons.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

@Service
@Slf4j
public class IconArtifactCleanupService {

    public BufferedImage cleanupIconArtifacts(BufferedImage croppedIcon, int row, int col) {
        int width = croppedIcon.getWidth();
        int height = croppedIcon.getHeight();

        log.debug("Cleaning up artifacts for icon at [{},{}] with size {}x{}", row, col, width, height);

        BufferedImage cleanedIcon = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = cleanedIcon.createGraphics();
        g2d.drawImage(croppedIcon, 0, 0, null);
        g2d.dispose();

        Rectangle mainContentBounds = detectMainIconBounds(cleanedIcon);

        if (mainContentBounds == null) {
            log.debug("No main content detected in icon at [{},{}], skipping artifact cleanup", row, col);
            return cleanedIcon;
        }

        log.debug("Main content bounds for icon [{},{}]: x={}, y={}, width={}, height={}",
                row, col, mainContentBounds.x, mainContentBounds.y, mainContentBounds.width, mainContentBounds.height);

        int edgeThickness = Math.min(Math.max(width / 20, 3), 15);

        int artifactsRemoved = 0;

        if (col > 0) {
            artifactsRemoved += cleanupEdgeArtifacts(cleanedIcon, mainContentBounds, 0, 0, edgeThickness, height, "left");
        }

        if (col < 2) {
            artifactsRemoved += cleanupEdgeArtifacts(cleanedIcon, mainContentBounds, width - edgeThickness, 0, edgeThickness, height, "right");
        }

        if (row > 0) {
            artifactsRemoved += cleanupEdgeArtifacts(cleanedIcon, mainContentBounds, 0, 0, width, edgeThickness, "top");
        }

        if (row < 2) {
            artifactsRemoved += cleanupEdgeArtifacts(cleanedIcon, mainContentBounds, 0, height - edgeThickness, width, edgeThickness, "bottom");
        }

        artifactsRemoved += cleanupCornerArtifacts(cleanedIcon, mainContentBounds, row, col);

        log.debug("Removed {} artifact pixels from icon at [{},{}]", artifactsRemoved, row, col);
        return cleanedIcon;
    }

    private Rectangle detectMainIconBounds(BufferedImage icon) {
        int width = icon.getWidth();
        int height = icon.getHeight();

        boolean[][] visited = new boolean[height][width];
        Rectangle largestBounds = null;
        int largestArea = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[y][x] && isContentPixel(icon.getRGB(x, y))) {
                    Rectangle componentBounds = findConnectedComponentBounds(icon, x, y, visited);
                    int area = componentBounds.width * componentBounds.height;

                    if (area > largestArea) {
                        largestArea = area;
                        largestBounds = componentBounds;
                    }
                }
            }
        }

        if (largestBounds != null) {
            int expansion = Math.min(5, Math.min(width, height) / 20);
            largestBounds.x = Math.max(0, largestBounds.x - expansion);
            largestBounds.y = Math.max(0, largestBounds.y - expansion);
            largestBounds.width = Math.min(width - largestBounds.x, largestBounds.width + 2 * expansion);
            largestBounds.height = Math.min(height - largestBounds.y, largestBounds.height + 2 * expansion);
        }

        return largestBounds;
    }

    private Rectangle findConnectedComponentBounds(BufferedImage icon, int startX, int startY, boolean[][] visited) {
        int width = icon.getWidth();
        int height = icon.getHeight();

        Queue<int[]> queue = new LinkedList<>();
        queue.offer(new int[]{startX, startY});
        visited[startY][startX] = true;

        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;

        int[] dx = {-1, 1, 0, 0};
        int[] dy = {0, 0, -1, 1};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0], y = current[1];

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];

                if (nx >= 0 && nx < width && ny >= 0 && ny < height &&
                        !visited[ny][nx] && isContentPixel(icon.getRGB(nx, ny))) {
                    visited[ny][nx] = true;
                    queue.offer(new int[]{nx, ny});
                }
            }
        }

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private int cleanupEdgeArtifacts(BufferedImage icon, Rectangle mainBounds, int x, int y, int width, int height, String edge) {
        int artifactsRemoved = 0;

        for (int py = y; py < y + height && py < icon.getHeight(); py++) {
            for (int px = x; px < x + width && px < icon.getWidth(); px++) {
                int rgb = icon.getRGB(px, py);

                if (isContentPixel(rgb)) {
                    boolean isArtifact = false;

                    if (!mainBounds.contains(px, py)) {
                        isArtifact = true;
                    }

                    if (!isArtifact && isIsolatedPixel(icon, px, py, mainBounds)) {
                        isArtifact = true;
                    }

                    if (!isArtifact && hasColorDiscontinuity(icon, px, py, mainBounds)) {
                        isArtifact = true;
                    }

                    if (isArtifact) {
                        icon.setRGB(px, py, 0x00000000);
                        artifactsRemoved++;
                    }
                }
            }
        }

        if (artifactsRemoved > 0) {
            log.debug("Removed {} artifacts from {} edge", artifactsRemoved, edge);
        }

        return artifactsRemoved;
    }

    private int cleanupCornerArtifacts(BufferedImage icon, Rectangle mainBounds, int row, int col) {
        int width = icon.getWidth();
        int height = icon.getHeight();
        int cornerSize = Math.min(Math.max(width / 15, 5), 20);
        int artifactsRemoved = 0;

        Rectangle[] corners = new Rectangle[0];

        if (row > 0 && col > 0) {
            corners = Arrays.copyOf(corners, corners.length + 1);
            corners[corners.length - 1] = new Rectangle(0, 0, cornerSize, cornerSize);
        }
        if (row > 0 && col < 2) {
            corners = Arrays.copyOf(corners, corners.length + 1);
            corners[corners.length - 1] = new Rectangle(width - cornerSize, 0, cornerSize, cornerSize);
        }
        if (row < 2 && col > 0) {
            corners = Arrays.copyOf(corners, corners.length + 1);
            corners[corners.length - 1] = new Rectangle(0, height - cornerSize, cornerSize, cornerSize);
        }
        if (row < 2 && col < 2) {
            corners = Arrays.copyOf(corners, corners.length + 1);
            corners[corners.length - 1] = new Rectangle(width - cornerSize, height - cornerSize, cornerSize, cornerSize);
        }

        for (Rectangle corner : corners) {
            for (int y = corner.y; y < corner.y + corner.height && y < height; y++) {
                for (int x = corner.x; x < corner.x + corner.width && x < width; x++) {
                    int rgb = icon.getRGB(x, y);

                    if (isContentPixel(rgb) && !mainBounds.contains(x, y)) {
                        icon.setRGB(x, y, 0x00000000);
                        artifactsRemoved++;
                    }
                }
            }
        }

        if (artifactsRemoved > 0) {
            log.debug("Removed {} corner artifacts", artifactsRemoved);
        }

        return artifactsRemoved;
    }

    private boolean isContentPixel(int rgb) {
        int alpha = (rgb >> 24) & 0xFF;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        boolean isTransparent = alpha < 50;
        boolean isWhite = red > 240 && green > 240 && blue > 240;

        return !isTransparent && !isWhite;
    }

    private boolean isIsolatedPixel(BufferedImage icon, int x, int y, Rectangle mainBounds) {
        int radius = 3;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dy == 0) continue;

                int nx = x + dx;
                int ny = y + dy;

                if (nx >= 0 && nx < icon.getWidth() && ny >= 0 && ny < icon.getHeight()) {
                    if (isContentPixel(icon.getRGB(nx, ny)) && mainBounds.contains(nx, ny)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean hasColorDiscontinuity(BufferedImage icon, int x, int y, Rectangle mainBounds) {
        int rgb = icon.getRGB(x, y);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        int nearestDistance = Integer.MAX_VALUE;
        int nearestRGB = rgb;

        for (int my = Math.max(0, mainBounds.y - 5); my < Math.min(icon.getHeight(), mainBounds.y + mainBounds.height + 5); my++) {
            for (int mx = Math.max(0, mainBounds.x - 5); mx < Math.min(icon.getWidth(), mainBounds.x + mainBounds.width + 5); mx++) {
                if (mainBounds.contains(mx, my) && isContentPixel(icon.getRGB(mx, my))) {
                    int distance = Math.abs(mx - x) + Math.abs(my - y);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestRGB = icon.getRGB(mx, my);
                    }
                }
            }
        }

        if (nearestDistance == Integer.MAX_VALUE) {
            return false;
        }

        int nearestRed = (nearestRGB >> 16) & 0xFF;
        int nearestGreen = (nearestRGB >> 8) & 0xFF;
        int nearestBlue = nearestRGB & 0xFF;

        int colorDistance = Math.abs(red - nearestRed) + Math.abs(green - nearestGreen) + Math.abs(blue - nearestBlue);

        return colorDistance > 150;
    }
}
