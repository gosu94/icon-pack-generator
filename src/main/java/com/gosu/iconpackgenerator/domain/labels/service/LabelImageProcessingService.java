package com.gosu.iconpackgenerator.domain.labels.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
@Slf4j
public class LabelImageProcessingService {

    private static final int MIN_LABEL_DIMENSION = 256;

    /**
     * Crop the generated label image to the bounding box of visible content and ensure
     * the result has a minimum dimension by upscaling if necessary.
     * Falls back to the original image if bounds cannot be detected.
     */
    public String cropToContent(String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return base64Image;
        }

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (source == null) {
                log.warn("Could not read label image, returning original data");
                return base64Image;
            }

            Rectangle bounds = detectContentBounds(source);
            if (bounds == null) {
                log.debug("No content bounds detected for label, returning original image");
                return base64Image;
            }

            BufferedImage cropped = source.getSubimage(
                    Math.max(0, bounds.x),
                    Math.max(0, bounds.y),
                    Math.min(bounds.width, source.getWidth() - bounds.x),
                    Math.min(bounds.height, source.getHeight() - bounds.y)
            );

            BufferedImage resized = resizeIfNeeded(cropped);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("Failed to crop label image, returning original", e);
            return base64Image;
        }
    }

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

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                boolean isTransparent = alpha < 10;
                boolean isWhite = red > 245 && green > 245 && blue > 245;
                boolean isBlack = red < 10 && green < 10 && blue < 10;

                if (isTransparent) {
                    transparentPixels++;
                    continue;
                }
                if (isWhite) {
                    whitePixels++;
                    continue;
                }
                if (isBlack) {
                    blackPixels++;
                }

                hasContent = true;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (!hasContent) {
            double blackRatio = (double) blackPixels / Math.max(1, totalPixels);
            if (blackRatio > 0.1) {
                log.debug("Label image mostly dark, retrying including black pixels");
                return detectContentBoundsIncludingBlack(image);
            }
            return null;
        }

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

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
            return null;
        }

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private BufferedImage resizeIfNeeded(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int minDimension = Math.min(width, height);

        if (minDimension >= MIN_LABEL_DIMENSION) {
            return image;
        }

        double scale = (double) MIN_LABEL_DIMENSION / minDimension;
        int targetWidth = (int) Math.round(width * scale);
        int targetHeight = (int) Math.round(height * scale);

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }
}
