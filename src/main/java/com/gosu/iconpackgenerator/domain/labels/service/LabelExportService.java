package com.gosu.iconpackgenerator.domain.labels.service;

import com.gosu.iconpackgenerator.domain.labels.dto.LabelExportRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationResponse;
import com.gosu.iconpackgenerator.domain.vectorization.SvgVectorizationService;
import com.gosu.iconpackgenerator.domain.vectorization.VectorizationException;
import dev.matrixlab.webp4j.NativeWebP;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabelExportService {

    private final SvgVectorizationService svgVectorizationService;

    private static final int MIN_VECTORIZE_HEIGHT = 256;

    private Boolean webpAvailable = null;
    private NativeWebP nativeWebP = null;

    public byte[] createLabelPackZip(LabelExportRequest exportRequest) {
        List<String> formats = exportRequest.getFormats();
        if (formats == null || formats.isEmpty()) {
            formats = List.of("png", "webp");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            int index = 1;
            for (LabelGenerationResponse.GeneratedLabel label : exportRequest.getLabels()) {
                if (label == null || label.getBase64Data() == null) {
                    index++;
                    continue;
                }

                byte[] originalBytes = Base64.getDecoder().decode(label.getBase64Data());
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
                if (bufferedImage == null) {
                    log.warn("Failed to read label image for export {}", index);
                    index++;
                    continue;
                }

                String baseName = String.format("label_%d", index);

                if (formats.contains("png")) {
                    byte[] pngData = encodeImage(bufferedImage, "png");
                    addEntry(zos, String.format("labels/%s.png", baseName), pngData);
                }

                if (formats.contains("webp") && isWebpAvailable()) {
                    try {
                        byte[] webpData = encodeImageToWebP(bufferedImage);
                        addEntry(zos, String.format("labels/%s.webp", baseName), webpData);
                    } catch (Exception e) {
                        log.warn("Failed to encode label {} to WebP: {}", index, e.getMessage());
                    }
                }

                if (exportRequest.isVectorizeSvg()) {
                    byte[] vectorizationBytes = originalBytes;
                    if (bufferedImage.getHeight() < MIN_VECTORIZE_HEIGHT) {
                        vectorizationBytes = extendImageHeight(bufferedImage, MIN_VECTORIZE_HEIGHT);
                    }

                    byte[] vectorizedSvg = svgVectorizationService.vectorizeImage(vectorizationBytes, baseName);
                    if (vectorizedSvg != null && vectorizedSvg.length > 0) {
                        addEntry(zos, String.format("vectorized-svg/%s.svg", baseName), vectorizedSvg);
                    } else {
                        throw new VectorizationException("Vectorized SVG empty for label " + index);
                    }
                }

                index++;
            }

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error creating label export zip", e);
            throw new RuntimeException("Failed to create label export", e);
        }
    }

    private void addEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private byte[] encodeImage(BufferedImage image, String format) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode label image to " + format, e);
        }
    }

    private byte[] extendImageHeight(BufferedImage image, int targetHeight) {
        int width = image.getWidth();
        BufferedImage extendedImage = new BufferedImage(width, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = extendedImage.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return encodeImage(extendedImage, "png");
    }

    private boolean isWebpAvailable() {
        if (webpAvailable == null) {
            try {
                nativeWebP = new NativeWebP();
                webpAvailable = true;
            } catch (Exception e) {
                log.warn("WebP4j library not available, skipping WebP export: {}", e.getMessage());
                webpAvailable = false;
                nativeWebP = null;
            }
        }
        return webpAvailable;
    }

    private byte[] encodeImageToWebP(BufferedImage image) throws Exception {
        if (nativeWebP == null) {
            throw new Exception("NativeWebP not initialized");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        boolean hasAlpha = image.getColorModel().hasAlpha();

        byte[] imageBytes = convertBufferedImageToBytes(image);
        if (imageBytes.length == 0) {
            throw new Exception("Failed to convert BufferedImage to raw bytes");
        }

        int stride = width * (hasAlpha ? 4 : 3);
        float quality = 80.0f;

        byte[] encodedWebP = hasAlpha
                ? nativeWebP.encodeRGBA(imageBytes, width, height, stride, quality)
                : nativeWebP.encodeRGB(imageBytes, width, height, stride, quality);

        if (encodedWebP == null || encodedWebP.length == 0) {
            throw new Exception("WebP encoding returned empty data");
        }

        return encodedWebP;
    }

    private byte[] convertBufferedImageToBytes(BufferedImage image) {
        boolean hasAlpha = image.getColorModel().hasAlpha();
        int width = image.getWidth();
        int height = image.getHeight();
        int bytesPerPixel = hasAlpha ? 4 : 3;

        byte[] output = new byte[width * height * bytesPerPixel];
        int[] rowBuffer = new int[width];
        int index = 0;

        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, rowBuffer, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = rowBuffer[x];
                if (hasAlpha) {
                    output[index++] = (byte) ((argb >> 16) & 0xFF);
                    output[index++] = (byte) ((argb >> 8) & 0xFF);
                    output[index++] = (byte) (argb & 0xFF);
                    output[index++] = (byte) ((argb >> 24) & 0xFF);
                } else {
                    output[index++] = (byte) ((argb >> 16) & 0xFF);
                    output[index++] = (byte) ((argb >> 8) & 0xFF);
                    output[index++] = (byte) (argb & 0xFF);
                }
            }
        }

        return output;
    }
}
