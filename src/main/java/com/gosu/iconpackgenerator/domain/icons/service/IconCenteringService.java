package com.gosu.iconpackgenerator.domain.icons.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;

@Service
@Slf4j
public class IconCenteringService {

    public BufferedImage centerIcon(BufferedImage iconImage, int targetSize) {
        if (iconImage == null) {
            throw new IllegalArgumentException("Icon image cannot be null");
        }

        log.debug("Centering icon of size {}x{}", iconImage.getWidth(), iconImage.getHeight());

        Rectangle contentBounds = detectContentBounds(iconImage);

        if (contentBounds == null || contentBounds.width == 0 || contentBounds.height == 0) {
            log.warn("No content detected in icon, returning original image");
            return iconImage;
        }

        log.debug("Content bounds detected: x={}, y={}, width={}, height={}",
                contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);

        BufferedImage croppedContent = iconImage.getSubimage(
                contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);

        if (targetSize <= 0) {
            targetSize = Math.max(iconImage.getWidth(), iconImage.getHeight());
        }

        BufferedImage centeredIcon = createCenteredCanvas(croppedContent, targetSize);

        log.debug("Successfully centered icon to {}x{} canvas", targetSize, targetSize);
        return centeredIcon;
    }

    private Rectangle detectContentBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        int backgroundThreshold = 240;

        boolean hasTransparent = image.getColorModel().hasAlpha();
        boolean foundContent = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                if (hasTransparent && alpha < 10) {
                    continue;
                }

                if (red > backgroundThreshold && green > backgroundThreshold && blue > backgroundThreshold) {
                    continue;
                }

                foundContent = true;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (!foundContent) {
            log.debug("No content found with strict detection. Falling back to more lenient detection.");
            return detectContentBoundsIncludingBlack(image);
        }

        return new Rectangle(minX, minY, Math.max(1, maxX - minX + 1), Math.max(1, maxY - minY + 1));
    }

    private Rectangle detectContentBoundsIncludingBlack(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        int backgroundThreshold = 245;
        boolean foundContent = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                boolean isTransparent = alpha < 5;
                boolean isWhite = red > backgroundThreshold &&
                        green > backgroundThreshold &&
                        blue > backgroundThreshold;

                if (!isTransparent && !isWhite) {
                    foundContent = true;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (!foundContent) {
            return null;
        }

        return new Rectangle(minX, minY, Math.max(1, maxX - minX + 1), Math.max(1, maxY - minY + 1));
    }

    private BufferedImage createCenteredCanvas(BufferedImage contentImage, int canvasSize) {
        BufferedImage canvas = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = canvas.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, canvasSize, canvasSize);
        g2d.setComposite(AlphaComposite.SrcOver);

        int contentWidth = contentImage.getWidth();
        int contentHeight = contentImage.getHeight();

        double maxContentSize = canvasSize * 0.9;
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

        g2d.drawImage(contentImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();

        return canvas;
    }
}
