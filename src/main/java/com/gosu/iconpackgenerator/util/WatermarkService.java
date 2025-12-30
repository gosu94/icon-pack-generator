package com.gosu.iconpackgenerator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
@Slf4j
public class WatermarkService {

    private static final String WATERMARK_TEXT = "IPG trial";
    private static final float WATERMARK_ALPHA = 0.55f;
    private static final double WATERMARK_ANGLE_DEGREES = -25.0;

    public String applyTrialWatermark(String base64Data) {
        if (base64Data == null || base64Data.isBlank()) {
            return base64Data;
        }

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                return base64Data;
            }

            BufferedImage watermarked = new BufferedImage(
                    original.getWidth(),
                    original.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);

            Graphics2D graphics = watermarked.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.drawImage(original, 0, 0, null);

            int shortestSide = Math.min(original.getWidth(), original.getHeight());
            int fontSize = Math.max(18, Math.round(shortestSide / 6f));
            graphics.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics metrics = graphics.getFontMetrics();
            int textWidth = metrics.stringWidth(WATERMARK_TEXT);
            int textHeight = metrics.getAscent();

            double centerX = original.getWidth() / 2.0;
            double centerY = original.getHeight() / 2.0;

            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATERMARK_ALPHA));
            graphics.setColor(new Color(255, 255, 255));
            graphics.rotate(Math.toRadians(WATERMARK_ANGLE_DEGREES), centerX, centerY);
            graphics.drawString(
                    WATERMARK_TEXT,
                    (int) Math.round(centerX - textWidth / 2.0),
                    (int) Math.round(centerY + textHeight / 2.0));
            graphics.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(watermarked, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.warn("Failed to apply trial watermark", e);
            return base64Data;
        }
    }
}
