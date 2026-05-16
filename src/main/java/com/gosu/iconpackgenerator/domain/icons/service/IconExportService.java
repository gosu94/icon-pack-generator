package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.domain.ai.IdeoGramRemoveBackGroundService;
import com.gosu.iconpackgenerator.domain.ai.SeedVrUpscaleService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.vectorization.SvgVectorizationService;
import dev.matrixlab.webp4j.NativeWebP;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class IconExportService {

    private final SvgVectorizationService svgVectorizationService;
    private final SeedVrUpscaleService seedVrUpscaleService;
    private final IdeoGramRemoveBackGroundService ideoGramRemoveBackGroundService;
    
    // Standard icon sizes for PNG exports
    private static final int[] PNG_SIZES = {32, 64, 128, 256, 512};
    
    // Comprehensive ICO sizes for modern applications and high-DPI displays
    private static final int[] ICO_SIZES = {32, 48, 64, 128, 256};

    private static final int HQ_ADDITIONAL_SIZE = 1024;
    private static final int HQ_UPSCALE_SOURCE_SIZE = 256;
    private static final float HQ_UPSCALE_FACTOR = 4.0f;
    private static final int HQ_THREAD_POOL_SIZE = 9;
    private static final Color HQ_UPSCALE_BACKGROUND = Color.WHITE;
    
    // Cache for WebP4j availability to avoid repeated checks
    private Boolean webp4jAvailable = null;
    private NativeWebP nativeWebP = null;

    public byte[] createIconPackZip(IconExportRequest exportRequest) {
        List<String> formats = exportRequest.getFormats();
        if (formats == null || formats.isEmpty()) {
            log.info("No formats specified, defaulting to all available formats.");
            formats = new java.util.ArrayList<>(java.util.Arrays.asList("svg", "png", "ico"));
            if (isWebp4jAvailable()) {
                formats.add("webp");
            }
        }

        log.info("Creating icon pack for request: {} with {} icons and formats: {}",
                exportRequest.getRequestId(), exportRequest.getIcons().size(), formats);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            List<PreparedIcon> preparedIcons = prepareIcons(exportRequest.getIcons());
            boolean needsVectorization = exportRequest.isVectorizeSvg();
            Map<String, byte[]> vectorizedSvgs = needsVectorization
                    ? svgVectorizationService.vectorizeImages(
                    preparedIcons.stream()
                            .collect(Collectors.toMap(PreparedIcon::baseName, PreparedIcon::originalData)))
                    : Collections.emptyMap();
            Map<String, byte[]> hqIconData = exportRequest.isHqUpscale()
                    ? processIconsForHighQuality(preparedIcons)
                    : Collections.emptyMap();
            int[] rasterSizes = getRasterSizes(exportRequest.isHqUpscale());

            for (PreparedIcon preparedIcon : preparedIcons) {
                String iconBase64Data = preparedIcon.icon().getBase64Data();
                byte[] originalIconData = preparedIcon.originalData();
                String baseName = preparedIcon.baseName();
                byte[] workingIconData = exportRequest.isHqUpscale()
                        ? hqIconData.getOrDefault(baseName, originalIconData)
                        : originalIconData;

                if (formats.contains("svg")) {
                    createSvgVersion(zos, iconBase64Data, baseName);
                }
                if (formats.contains("png")) {
                    createPngVersions(zos, workingIconData, baseName, rasterSizes);
                }
                if (formats.contains("webp") && isWebp4jAvailable()) {
                    createWebpVersions(zos, workingIconData, baseName, rasterSizes);
                }
                if (formats.contains("ico")) {
                    createIcoVersion(zos, workingIconData, baseName);
                }
                if (exportRequest.isVectorizeSvg()) {
                    byte[] vectorizedSvg = vectorizedSvgs.get(baseName);
                    if (vectorizedSvg != null && vectorizedSvg.length > 0) {
                        createVectorizedSvgEntry(zos, baseName, vectorizedSvg);
                    } else {
                        log.warn("Vectorized SVG not available for icon {} ({})", baseName, preparedIcon.icon().getDescription());
                    }
                }

                log.debug("Created requested formats for icon ({})", baseName);
            }

            zos.finish();
            log.info("Successfully created icon pack ZIP with {} icons in formats: {}", exportRequest.getIcons().size(), formats);
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Error creating ZIP file for request: {}", exportRequest.getRequestId(), e);
            throw new RuntimeException("Failed to create icon pack ZIP", e);
        }
    }

    private List<PreparedIcon> prepareIcons(List<IconGenerationResponse.GeneratedIcon> icons) {
        List<PreparedIcon> preparedIcons = new ArrayList<>();
        int iconIndex = 1;
        for (IconGenerationResponse.GeneratedIcon icon : icons) {
            if (icon == null || icon.getBase64Data() == null) {
                throw new IllegalArgumentException("Icon data missing for export at index " + iconIndex);
            }
            byte[] originalIconData = Base64.getDecoder().decode(icon.getBase64Data());
            String baseName = createBaseName(icon, iconIndex);
            preparedIcons.add(new PreparedIcon(baseName, icon, originalIconData));
            iconIndex++;
        }
        return preparedIcons;
    }

    private void createVectorizedSvgEntry(ZipOutputStream zos, String baseName, byte[] svgBytes) throws IOException {
        ZipEntry zipEntry = new ZipEntry("vectorized-svg/" + baseName + ".svg");
        zos.putNextEntry(zipEntry);
        zos.write(svgBytes);
        zos.closeEntry();
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
    
    private void createPngVersions(ZipOutputStream zos, byte[] iconData, String baseName, int[] targetSizes) throws IOException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(iconData);
            BufferedImage originalImage = ImageIO.read(bais);
            
            if (originalImage == null) {
                log.warn("Could not read image for PNG conversion: {}", baseName);
                return;
            }
            
            for (int size : targetSizes) {
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
     * Check if WebP4j library is available (cached result)
     */
    private boolean isWebp4jAvailable() {
        if (webp4jAvailable == null) {
        try {
            // Try to create NativeWebP instance to test if the library is available
            nativeWebP = new NativeWebP();
            webp4jAvailable = true;
            log.info("WebP4j library loaded successfully - WebP export will be available");
        } catch (Exception e) {
            webp4jAvailable = false;
            nativeWebP = null;
            log.warn("WebP4j library not available. WebP export will be skipped. Error: {}", e.getMessage());
        }
        }
        return webp4jAvailable;
    }

    private void createWebpVersions(ZipOutputStream zos, byte[] iconData, String baseName, int[] targetSizes) throws IOException {
        // This method should only be called when WebP4j is available
        // The availability check is done in the calling method
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(iconData);
            BufferedImage originalImage = ImageIO.read(bais);

            if (originalImage == null) {
                log.warn("Could not read image for WEBP conversion: {}", baseName);
                return;
            }

            for (int size : targetSizes) {
                BufferedImage resizedImage = resizeImage(originalImage, size, size);
                
                try {
                    byte[] webpData = encodeImageToWebP(resizedImage);
                    
                    // Verify the WEBP data is not empty
                    if (webpData.length == 0) {
                        log.warn("WebP conversion resulted in empty data for {}_{}x{}", baseName, size, size);
                        continue;
                    }

                    ZipEntry zipEntry = new ZipEntry(String.format("webp/%s_%dx%d.webp", baseName, size, size));
                    zos.putNextEntry(zipEntry);
                    zos.write(webpData);
                    zos.closeEntry();
                    
                    log.debug("Successfully created WebP: {}_{}x{}.webp ({} bytes)", baseName, size, size, webpData.length);
                    
                } catch (Exception webpError) {
                    log.warn("Failed to convert {}_{}x{} to WebP format: {}. Skipping this size.", 
                            baseName, size, size, webpError.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Failed to create WEBP versions for: {}", baseName, e);
        }
    }
    
    private void createIcoVersion(ZipOutputStream zos, byte[] iconData, String baseName) throws IOException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(iconData);
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

    /**
     * Encode BufferedImage to WebP format using WebP4j
     */
    private byte[] encodeImageToWebP(BufferedImage image) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean hasAlpha = image.getColorModel().hasAlpha();
        
        if (nativeWebP == null) {
            throw new Exception("NativeWebP not initialized");
        }
        
        // Convert BufferedImage to byte array based on the example WebPCodec
        byte[] imageBytes = convertBufferedImageToBytes(image);
        if (imageBytes.length == 0) {
            throw new Exception("Failed to convert BufferedImage to byte array");
        }
        
        // Calculate stride (bytes per row)
        int stride = width * (hasAlpha ? 4 : 3);
        float quality = 75.0f;
        
        // Encode using the native library (following the WebPCodec example pattern)
        byte[] encodedWebP = hasAlpha 
            ? nativeWebP.encodeRGBA(imageBytes, width, height, stride, quality)
            : nativeWebP.encodeRGB(imageBytes, width, height, stride, quality);
            
        if (encodedWebP == null || encodedWebP.length == 0) {
            throw new Exception("WebP encoding failed - native library returned null or empty result");
        }
        
        return encodedWebP;
    }
    
    /**
     * Convert BufferedImage to byte array (RGB or RGBA based on alpha channel)
     * Based on the WebPCodec example implementation
     */
    private byte[] convertBufferedImageToBytes(BufferedImage image) {
        boolean hasAlpha = image.getColorModel().hasAlpha();
        int width = image.getWidth();
        int height = image.getHeight();
        int bytesPerPixel = hasAlpha ? 4 : 3;
        
        byte[] output = new byte[width * height * bytesPerPixel];
        
        // Use efficient row-by-row processing (from the WebPCodec example)
        int[] rowBuffer = new int[width];
        int index = 0;
        
        for (int y = 0; y < height; y++) {
            // Get entire row at once for better performance
            image.getRGB(0, y, width, 1, rowBuffer, 0, width);
            
            if (hasAlpha) {
                for (int x = 0; x < width; x++) {
                    int argb = rowBuffer[x];
                    output[index++] = (byte) ((argb >> 16) & 0xFF); // Red
                    output[index++] = (byte) ((argb >> 8) & 0xFF);  // Green
                    output[index++] = (byte) (argb & 0xFF);         // Blue
                    output[index++] = (byte) ((argb >> 24) & 0xFF); // Alpha
                }
            } else {
                for (int x = 0; x < width; x++) {
                    int argb = rowBuffer[x];
                    output[index++] = (byte) ((argb >> 16) & 0xFF); // Red
                    output[index++] = (byte) ((argb >> 8) & 0xFF);  // Green
                    output[index++] = (byte) (argb & 0xFF);         // Blue
                }
            }
        }
        
        return output;
    }

    private int[] getRasterSizes(boolean includeHqSize) {
        if (!includeHqSize) {
            return PNG_SIZES;
        }
        int[] sizes = Arrays.copyOf(PNG_SIZES, PNG_SIZES.length + 1);
        sizes[sizes.length - 1] = HQ_ADDITIONAL_SIZE;
        return sizes;
    }

    private Map<String, byte[]> processIconsForHighQuality(List<PreparedIcon> preparedIcons) {
        if (preparedIcons.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, byte[]> processedIcons = new ConcurrentHashMap<>();
        log.info("Starting HQ raster upscale for {} icons", preparedIcons.size());
        ExecutorService executor = Executors.newFixedThreadPool(HQ_THREAD_POOL_SIZE);

        try {
            List<CompletableFuture<Void>> tasks = preparedIcons.stream()
                    .map(icon -> CompletableFuture.runAsync(() -> {
                        try {
                            byte[] preparedImage = prepareImageForHighQualityUpscale(icon.originalData(), icon.baseName());
                            byte[] upscaledImage = seedVrUpscaleService.upscaleImage(preparedImage, HQ_UPSCALE_FACTOR).join();
                            byte[] transparentImage = ideoGramRemoveBackGroundService.removeBackground(upscaledImage);
                            processedIcons.put(icon.baseName(), transparentImage);
                        } catch (Exception e) {
                            log.error("HQ raster upscale failed for icon {}. Using original raster.", icon.baseName(), e);
                            processedIcons.put(icon.baseName(), icon.originalData());
                        }
                    }, executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
            log.info("Completed HQ raster upscale for {} icons", processedIcons.size());
        }

        return processedIcons;
    }

    private byte[] prepareImageForHighQualityUpscale(byte[] imageData, String baseName) throws IOException {
        BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(imageData));
        if (sourceImage == null) {
            throw new IOException("Could not read icon image for HQ upscale: " + baseName);
        }

        BufferedImage resizedImage = resizeImage(sourceImage, HQ_UPSCALE_SOURCE_SIZE, HQ_UPSCALE_SOURCE_SIZE);
        BufferedImage preparedImage = replaceTransparentPixels(resizedImage, HQ_UPSCALE_BACKGROUND);

        log.debug("Prepared icon {} for HQ upscale at {}x{} with white background",
                baseName,
                HQ_UPSCALE_SOURCE_SIZE,
                HQ_UPSCALE_SOURCE_SIZE);

        return imageToBytes(preparedImage, "png");
    }

    private BufferedImage replaceTransparentPixels(BufferedImage image, Color backgroundColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage processed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int backgroundArgb = backgroundColor.getRGB();
        int[] rowBuffer = new int[width];

        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, rowBuffer, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = rowBuffer[x];
                int alpha = (argb >> 24) & 0xFF;

                if (alpha == 255) {
                    continue;
                }

                if (alpha == 0) {
                    rowBuffer[x] = backgroundArgb;
                    continue;
                }

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                float alphaFactor = alpha / 255.0f;

                int blendedR = Math.round(alphaFactor * r + (1 - alphaFactor) * backgroundColor.getRed());
                int blendedG = Math.round(alphaFactor * g + (1 - alphaFactor) * backgroundColor.getGreen());
                int blendedB = Math.round(alphaFactor * b + (1 - alphaFactor) * backgroundColor.getBlue());

                rowBuffer[x] = (0xFF << 24) | (blendedR << 16) | (blendedG << 8) | blendedB;
            }
            processed.setRGB(0, y, width, 1, rowBuffer, 0, width);
        }

        return processed;
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

    private record PreparedIcon(String baseName, IconGenerationResponse.GeneratedIcon icon, byte[] originalData) {
    }
}
