package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.domain.ai.RecraftVectorizeModelService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class IconExportService {

    private final ImageProcessingService imageProcessingService;
    private final RecraftVectorizeModelService recraftVectorizeModelService;
    
    // Standard icon sizes for PNG exports
    private static final int[] PNG_SIZES = {32, 64, 128, 256, 512};
    
    // Comprehensive ICO sizes for modern applications and high-DPI displays
    private static final int[] ICO_SIZES = {32, 48, 64, 128, 256};
    private static final Pattern PATH_TAG_PATTERN = Pattern.compile("<path[^>]*?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILL_ATTR_PATTERN = Pattern.compile("fill\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern D_ATTR_PATTERN = Pattern.compile("d\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern RGB_FUNCTION_PATTERN = Pattern.compile("rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECT_PATH_DATA_PATTERN = Pattern.compile(
            "^M\\s*0(?:\\.0+)?\\s+0(?:\\.0+)?\\s+L\\s*([0-9]+(?:\\.[0-9]+)?)\\s+0(?:\\.0+)?\\s+L\\s*\\1\\s+([0-9]+(?:\\.[0-9]+)?)\\s+L\\s*0(?:\\.0+)?\\s+\\2\\s+L\\s*0(?:\\.0+)?\\s+0(?:\\.0+)?\\s+Z$",
            Pattern.CASE_INSENSITIVE);
    
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
            Map<String, byte[]> vectorizedSvgs = exportRequest.isVectorizeSvg()
                    ? generateVectorizedSvgs(preparedIcons)
                    : Collections.emptyMap();

            for (PreparedIcon preparedIcon : preparedIcons) {
                String iconBase64Data = preparedIcon.icon().getBase64Data();
                byte[] originalIconData = preparedIcon.originalData();
                String baseName = preparedIcon.baseName();

                if (formats.contains("svg")) {
                    createSvgVersion(zos, iconBase64Data, baseName);
                }
                if (formats.contains("png")) {
                    createPngVersions(zos, originalIconData, baseName);
                }
                if (formats.contains("webp") && isWebp4jAvailable()) {
                    createWebpVersions(zos, originalIconData, baseName);
                }
                if (formats.contains("ico")) {
                    createIcoVersion(zos, originalIconData, baseName);
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

    private Map<String, byte[]> generateVectorizedSvgs(List<PreparedIcon> preparedIcons) {
        if (preparedIcons.isEmpty()) {
            return Collections.emptyMap();
        }

        int poolSize = Math.min(preparedIcons.size(), 9);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        try {
            List<CompletableFuture<VectorizedSvgResult>> futures = new ArrayList<>();
            for (PreparedIcon preparedIcon : preparedIcons) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    byte[] svgBytes = recraftVectorizeModelService.vectorizeImageBlocking(preparedIcon.originalData());
                    byte[] sanitized = sanitizeVectorizedSvg(svgBytes, preparedIcon.baseName());
                    return new VectorizedSvgResult(preparedIcon.baseName(), sanitized);
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            Map<String, byte[]> vectorizedSvgs = new HashMap<>();
            for (CompletableFuture<VectorizedSvgResult> future : futures) {
                VectorizedSvgResult result = future.join();
                vectorizedSvgs.put(result.baseName(), result.svgBytes());
            }

            log.info("Successfully vectorized {} icons for export", vectorizedSvgs.size());
            return vectorizedSvgs;

        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Vectorization failed for one or more icons", cause);
            throw new RuntimeException("Failed to vectorize icons for export", cause);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private byte[] sanitizeVectorizedSvg(byte[] svgBytes, String baseName) {
        if (svgBytes == null || svgBytes.length == 0) {
            return svgBytes;
        }

        String svgContent = new String(svgBytes, StandardCharsets.UTF_8);
        Matcher pathMatcher = PATH_TAG_PATTERN.matcher(svgContent);
        StringBuffer sanitizedSvg = new StringBuffer();
        boolean modified = false;

        while (pathMatcher.find()) {
            String pathTag = pathMatcher.group();
            String fillValue = extractAttribute(pathTag, FILL_ATTR_PATTERN);

            if (fillValue != null && isSolidBackgroundFill(fillValue)) {
                String pathData = extractAttribute(pathTag, D_ATTR_PATTERN);
                if (pathData != null && isFullCanvasRectangle(pathData)) {
                    String updatedTag = FILL_ATTR_PATTERN.matcher(pathTag).replaceFirst("fill=\"none\"");
                    pathMatcher.appendReplacement(sanitizedSvg, Matcher.quoteReplacement(updatedTag));
                    modified = true;
                    log.debug("Sanitized background rectangle for vectorized icon {}", baseName);
                    continue;
                }
            }

            pathMatcher.appendReplacement(sanitizedSvg, Matcher.quoteReplacement(pathTag));
        }

        pathMatcher.appendTail(sanitizedSvg);
        return modified ? sanitizedSvg.toString().getBytes(StandardCharsets.UTF_8) : svgBytes;
    }

    private String extractAttribute(String tag, Pattern pattern) {
        Matcher matcher = pattern.matcher(tag);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isSolidBackgroundFill(String fillValue) {
        if (fillValue == null) {
            return false;
        }
        String trimmed = fillValue.trim();
        Matcher rgbMatcher = RGB_FUNCTION_PATTERN.matcher(trimmed);
        if (rgbMatcher.find()) {
            try {
                int r = Integer.parseInt(rgbMatcher.group(1));
                int g = Integer.parseInt(rgbMatcher.group(2));
                int b = Integer.parseInt(rgbMatcher.group(3));
                return r <= 30 && g <= 30 && b <= 30;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean isFullCanvasRectangle(String pathData) {
        if (pathData == null || pathData.isBlank()) {
            return false;
        }

        String normalized = pathData.replace(",", " ").trim().replaceAll("\\s+", " ");
        return RECT_PATH_DATA_PATTERN.matcher(normalized).matches();
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

    private void createWebpVersions(ZipOutputStream zos, byte[] originalIconData, String baseName) throws IOException {
        // This method should only be called when WebP4j is available
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

    private record VectorizedSvgResult(String baseName, byte[] svgBytes) {
    }
}
