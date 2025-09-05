package com.gosu.iconpackgenerator.domain.service;

import com.gosu.iconpackgenerator.domain.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.dto.IconGenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class IconExportService {

    private final ImageProcessingService imageProcessingService;
    
    // Standard icon sizes for PNG exports
    private static final int[] PNG_SIZES = {16, 32, 64, 128, 256, 512};
    
    // Comprehensive ICO sizes for modern applications and high-DPI displays
    private static final int[] ICO_SIZES = {16, 32, 48, 64, 128, 256};
    
    // Cache for WebP writer availability to avoid repeated checks
    private Boolean webpWriterAvailable = null;

    public byte[] createIconPackZip(IconExportRequest exportRequest) {
        List<String> formats = exportRequest.getFormats();
        if (formats == null || formats.isEmpty()) {
            log.info("No formats specified, defaulting to all available formats.");
            formats = new java.util.ArrayList<>(java.util.Arrays.asList("svg", "png", "ico"));
            if (isWebpWriterAvailable()) {
                formats.add("webp");
            }
        }

        log.info("Creating icon pack for request: {} with {} icons and formats: {}",
                exportRequest.getRequestId(), exportRequest.getIcons().size(), formats);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            int iconIndex = 1;
            for (IconGenerationResponse.GeneratedIcon icon : exportRequest.getIcons()) {
                String iconBase64Data = icon.getBase64Data();
                byte[] originalIconData = Base64.getDecoder().decode(iconBase64Data);
                String baseName = createBaseName(icon, iconIndex);

                if (formats.contains("svg")) {
                    createSvgVersion(zos, iconBase64Data, baseName);
                }
                if (formats.contains("png")) {
                    createPngVersions(zos, originalIconData, baseName);
                }
                if (formats.contains("webp") && isWebpWriterAvailable()) {
                    createWebpVersions(zos, originalIconData, baseName);
                }
                if (formats.contains("ico")) {
                    createIcoVersion(zos, originalIconData, baseName);
                }

                log.debug("Created requested formats for icon {} ({})", iconIndex, baseName);
                iconIndex++;
            }

            zos.finish();
            log.info("Successfully created icon pack ZIP with {} icons in formats: {}", exportRequest.getIcons().size(), formats);
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Error creating ZIP file for request: {}", exportRequest.getRequestId(), e);
            throw new RuntimeException("Failed to create icon pack ZIP", e);
        }
    }
    
    private void createSvgVersion(ZipOutputStream zos, String iconBase64Data, String baseName) throws IOException {
        try {
            byte[] iconData = Base64.getDecoder().decode(iconBase64Data);
            ByteArrayInputStream bais = new ByteArrayInputStream(iconData);
            BufferedImage image = ImageIO.read(bais);
            
            if (image == null) {
                log.warn("Could not read image for SVG conversion: {}", baseName);
                return;
            }
            
            int width = image.getWidth();
            int height = image.getHeight();

            // Create proper SVG with embedded PNG data
            String svgContent = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <svg width="%d" height="%d" viewBox="0 0 %d %d" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
                  <image xlink:href="data:image/png;base64,%s" width="%d" height="%d" x="0" y="0"/>
                </svg>
                """, width, height, width, height, iconBase64Data, width, height);

            ZipEntry zipEntry = new ZipEntry("svg/" + baseName + ".svg");
            zos.putNextEntry(zipEntry);
            zos.write(svgContent.getBytes("UTF-8"));
            zos.closeEntry();
            
        } catch (IOException e) {
            log.error("Failed to create SVG version for: {}", baseName, e);
        }
    }
    
    private void createPngVersions(ZipOutputStream zos, byte[] originalIconData, String baseName) throws IOException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(originalIconData);
            BufferedImage originalImage = ImageIO.read(bais);
            
            if (originalImage == null) {
                log.warn("Could not read image for PNG conversion: {}", baseName);
                return;
            }
            
            for (int size : PNG_SIZES) {
                BufferedImage resizedImage = resizeImage(originalImage, size, size);
                byte[] resizedImageData = imageToBytes(resizedImage, "png");
                
                ZipEntry zipEntry = new ZipEntry(String.format("png/%s_%dx%d.png", baseName, size, size));
                zos.putNextEntry(zipEntry);
                zos.write(resizedImageData);
                zos.closeEntry();
            }
            
        } catch (IOException e) {
            log.error("Failed to create PNG versions for: {}", baseName, e);
        }
    }
    
    /**
     * Check if WebP ImageWriter is available (cached result)
     */
    private boolean isWebpWriterAvailable() {
        if (webpWriterAvailable == null) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            webpWriterAvailable = writers.hasNext();
            if (!webpWriterAvailable) {
                log.info("No WebP ImageWriter found. WebP export will be skipped. " +
                        "This is common on ARM64/Apple Silicon systems due to native library compatibility issues.");
            } else {
                log.debug("WebP ImageWriter available - WebP export will be included in icon packs");
            }
        }
        return webpWriterAvailable;
    }

    private void createWebpVersions(ZipOutputStream zos, byte[] originalIconData, String baseName) throws IOException {
        // This method should only be called when WebP writers are available
        // The availability check is done in the calling method
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(originalIconData);
            BufferedImage originalImage = ImageIO.read(bais);

            if (originalImage == null) {
                log.warn("Could not read image for WEBP conversion: {}", baseName);
                return;
            }

            for (int size : PNG_SIZES) {
                BufferedImage resizedImage = resizeImage(originalImage, size, size);
                
                try {
                    byte[] resizedImageData = imageToBytes(resizedImage, "webp");
                    
                    // Verify the WEBP data is not empty
                    if (resizedImageData.length == 0) {
                        log.warn("WebP conversion resulted in empty data for {}_{}x{}", baseName, size, size);
                        continue;
                    }

                    ZipEntry zipEntry = new ZipEntry(String.format("webp/%s_%dx%d.webp", baseName, size, size));
                    zos.putNextEntry(zipEntry);
                    zos.write(resizedImageData);
                    zos.closeEntry();
                    
                    log.debug("Successfully created WebP: {}_{}x{}.webp ({} bytes)", baseName, size, size, resizedImageData.length);
                    
                } catch (IOException webpError) {
                    log.warn("Failed to convert {}_{}x{} to WebP format: {}. Skipping this size.", 
                            baseName, size, size, webpError.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Failed to create WEBP versions for: {}", baseName, e);
        }
    }
    
    private void createIcoVersion(ZipOutputStream zos, byte[] originalIconData, String baseName) throws IOException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(originalIconData);
            BufferedImage originalImage = ImageIO.read(bais);
            
            if (originalImage == null) {
                log.warn("Could not read image for ICO conversion: {}", baseName);
                return;
            }
            
            // Create ICO with multiple sizes (favicon standard)
            byte[] icoData = createIcoFile(originalImage);
            
            ZipEntry zipEntry = new ZipEntry("ico/" + baseName + ".ico");
            zos.putNextEntry(zipEntry);
            zos.write(icoData);
            zos.closeEntry();
            
        } catch (Exception e) {
            log.error("Failed to create ICO version for: {}", baseName, e);
        }
    }
    
    private BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        // Use high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(originalImage, 0, 0, width, height, null);
        g2d.dispose();
        
        return resizedImage;
    }
    
    private byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
    
    private byte[] createIcoFile(BufferedImage originalImage) throws IOException {
        // Create ICO file with all standard favicon sizes: 16x16, 32x32, and 48x48
        ByteArrayOutputStream icoStream = new ByteArrayOutputStream();
        
        // ICO file header
        icoStream.write(new byte[]{0, 0}); // Reserved
        icoStream.write(new byte[]{1, 0}); // Type (1 = ICO)
        icoStream.write(new byte[]{(byte) ICO_SIZES.length, 0}); // Number of images
        
        // Prepare images and data for all sizes
        BufferedImage[] images = new BufferedImage[ICO_SIZES.length];
        byte[][] imageData = new byte[ICO_SIZES.length][];
        
        for (int i = 0; i < ICO_SIZES.length; i++) {
            int size = ICO_SIZES[i];
            images[i] = resizeImage(originalImage, size, size);
            imageData[i] = imageToBytes(images[i], "png");
        }
        
        int headerSize = 6 + (16 * ICO_SIZES.length); // 6 bytes header + 16 bytes per image entry
        
        // Image directory entries
        int currentOffset = headerSize;
        for (int i = 0; i < ICO_SIZES.length; i++) {
            int size = ICO_SIZES[i];
            
            icoStream.write(size == 256 ? 0 : size); // Width (0 means 256)
            icoStream.write(size == 256 ? 0 : size); // Height (0 means 256)
            icoStream.write(0);  // Color count (0 for PNG)
            icoStream.write(0);  // Reserved
            icoStream.write(new byte[]{1, 0}); // Color planes
            icoStream.write(new byte[]{32, 0}); // Bits per pixel
            writeInt32LE(icoStream, imageData[i].length); // Size of image data
            writeInt32LE(icoStream, currentOffset); // Offset to image data
            
            currentOffset += imageData[i].length;
        }
        
        // Write image data
        for (byte[] data : imageData) {
            icoStream.write(data);
        }
        
        return icoStream.toByteArray();
    }
    
    private void writeInt32LE(ByteArrayOutputStream stream, int value) throws IOException {
        stream.write(value & 0xFF);
        stream.write((value >> 8) & 0xFF);
        stream.write((value >> 16) & 0xFF);
        stream.write((value >> 24) & 0xFF);
    }

    private String createBaseName(IconGenerationResponse.GeneratedIcon icon, int index) {
        String description = icon.getDescription();

        if (description != null && !description.trim().isEmpty() &&
            !description.startsWith("Generated icon") && !description.startsWith("Icon ")) {
            String sanitized = description.trim()
                    .replaceAll("[^a-zA-Z0-9\\s\\-_]", "")
                    .replaceAll("\\s+", "_")
                    .toLowerCase();

            if (sanitized.length() > 30) {
                sanitized = sanitized.substring(0, 30);
            }

            return String.format("%02d_%s", index, sanitized);
        } else {
            return String.format("%02d_icon", index);
        }
    }
}