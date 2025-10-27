package com.gosu.iconpackgenerator.domain.vectorization;

import com.gosu.iconpackgenerator.domain.ai.RecraftVectorizeModelService;
import com.gosu.iconpackgenerator.domain.vectorization.VectorizationException;
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
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SvgVectorizationService {

    private static final Pattern PATH_TAG_PATTERN = Pattern.compile("<path[^>]*?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILL_ATTR_PATTERN = Pattern.compile("fill\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern D_ATTR_PATTERN = Pattern.compile("d\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern RGB_FUNCTION_PATTERN = Pattern.compile("rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECT_PATH_DATA_PATTERN = Pattern.compile(
            "^M\\s*0(?:\\.0+)?\\s+0(?:\\.0+)?\\s+L\\s*([0-9]+(?:\\.[0-9]+)?)\\s+0(?:\\.0+)?\\s+L\\s*\\1\\s+([0-9]+(?:\\.[0-9]+)?)\\s+L\\s*0(?:\\.0+)?\\s+\\2\\s+L\\s*0(?:\\.0+)?\\s+0(?:\\.0+)?\\s+Z$",
            Pattern.CASE_INSENSITIVE);

    private static final int VECTOR_BACKGROUND_R = 255;
    private static final int VECTOR_BACKGROUND_G = 255;
    private static final int VECTOR_BACKGROUND_B = 255;
    private static final int VECTOR_BACKGROUND_TOLERANCE = 10;
    private static final int VECTOR_BACKGROUND_ARGB =
            new Color(VECTOR_BACKGROUND_R, VECTOR_BACKGROUND_G, VECTOR_BACKGROUND_B, 255).getRGB();

    private final RecraftVectorizeModelService recraftVectorizeModelService;

    public Map<String, byte[]> vectorizeImages(Map<String, byte[]> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyMap();
        }

        int poolSize = Math.min(images.size(), 9);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        try {
            List<CompletableFuture<VectorizedSvgResult>> futures = new ArrayList<>();
            for (Map.Entry<String, byte[]> entry : images.entrySet()) {
                String baseName = entry.getKey();
                byte[] data = entry.getValue();
                futures.add(CompletableFuture.supplyAsync(
                        () -> new VectorizedSvgResult(baseName, vectorizeImage(data, baseName)),
                        executor
                ));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            Map<String, byte[]> result = new HashMap<>();
            for (CompletableFuture<VectorizedSvgResult> future : futures) {
                VectorizedSvgResult vectorizedSvg = future.join();
                result.put(vectorizedSvg.baseName(), vectorizedSvg.svgBytes());
            }

            log.info("Successfully vectorized {} image(s)", result.size());
            return result;

        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof VectorizationException vectorizationException) {
                throw vectorizationException;
            }
            log.error("Vectorization failed for one or more images", cause);
            throw new VectorizationException("Failed to vectorize images for export", cause);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public byte[] vectorizeImage(byte[] imageData, String baseName) {
        if (imageData == null || imageData.length == 0) {
            return new byte[0];
        }

        try {
            byte[] prepared = prepareImageForVectorization(imageData, baseName);
            byte[] svgBytes = recraftVectorizeModelService.vectorizeImageBlocking(prepared);
            return sanitizeVectorizedSvg(svgBytes, baseName);
        } catch (VectorizationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Vectorization failed for {}", baseName, e);
            throw new VectorizationException("Vectorization failed for " + baseName, e);
        }
    }

    public byte[] prepareImageForVectorization(byte[] imageData, String baseName) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageData)) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                log.warn("Could not read image data for vectorization preprocessing: {}", baseName);
                return imageData;
            }

            if (!image.getColorModel().hasAlpha()) {
                return imageData;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            BufferedImage processed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
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
                        rowBuffer[x] = VECTOR_BACKGROUND_ARGB;
                        continue;
                    }

                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    float alphaFactor = alpha / 255.0f;

                    int blendedR = Math.round(alphaFactor * r + (1 - alphaFactor) * VECTOR_BACKGROUND_R);
                    int blendedG = Math.round(alphaFactor * g + (1 - alphaFactor) * VECTOR_BACKGROUND_G);
                    int blendedB = Math.round(alphaFactor * b + (1 - alphaFactor) * VECTOR_BACKGROUND_B);

                    rowBuffer[x] = (0xFF << 24) | (blendedR << 16) | (blendedG << 8) | blendedB;
                }
                processed.setRGB(0, y, width, 1, rowBuffer, 0, width);
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(processed, "png", baos);
                return baos.toByteArray();
            }
        } catch (IOException e) {
            log.warn("Failed to prepare image {} for vectorization, using original data", baseName, e);
            return imageData;
        }
    }

    public byte[] sanitizeVectorizedSvg(byte[] svgBytes, String baseName) {
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
                    log.debug("Sanitized background rectangle for vectorized image {}", baseName);
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
        int[] rgb = parseFillColor(fillValue);
        if (rgb == null) {
            return false;
        }
        return isDarkBackgroundColor(rgb) || matchesNeutralVectorBackground(rgb);
    }

    private int[] parseFillColor(String fillValue) {
        if (fillValue == null) {
            return null;
        }

        String normalized = fillValue.trim().toLowerCase(Locale.ROOT);
        Matcher rgbMatcher = RGB_FUNCTION_PATTERN.matcher(normalized);
        if (rgbMatcher.find()) {
            try {
                int r = Integer.parseInt(rgbMatcher.group(1));
                int g = Integer.parseInt(rgbMatcher.group(2));
                int b = Integer.parseInt(rgbMatcher.group(3));
                return new int[]{r, g, b};
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        Matcher hexMatcher = HEX_COLOR_PATTERN.matcher(normalized);
        if (hexMatcher.matches()) {
            String hex = hexMatcher.group(1);
            try {
                if (hex.length() == 3) {
                    int r = Integer.parseInt(String.valueOf(hex.charAt(0)) + hex.charAt(0), 16);
                    int g = Integer.parseInt(String.valueOf(hex.charAt(1)) + hex.charAt(1), 16);
                    int b = Integer.parseInt(String.valueOf(hex.charAt(2)) + hex.charAt(2), 16);
                    return new int[]{r, g, b};
                } else if (hex.length() == 6 || hex.length() == 8) {
                    int r = Integer.parseInt(hex.substring(0, 2), 16);
                    int g = Integer.parseInt(hex.substring(2, 4), 16);
                    int b = Integer.parseInt(hex.substring(4, 6), 16);
                    return new int[]{r, g, b};
                }
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private boolean isDarkBackgroundColor(int[] rgb) {
        return rgb[0] <= 30 && rgb[1] <= 30 && rgb[2] <= 30;
    }

    private boolean matchesNeutralVectorBackground(int[] rgb) {
        return Math.abs(rgb[0] - VECTOR_BACKGROUND_R) <= VECTOR_BACKGROUND_TOLERANCE
                && Math.abs(rgb[1] - VECTOR_BACKGROUND_G) <= VECTOR_BACKGROUND_TOLERANCE
                && Math.abs(rgb[2] - VECTOR_BACKGROUND_B) <= VECTOR_BACKGROUND_TOLERANCE;
    }

    private boolean isFullCanvasRectangle(String pathData) {
        if (pathData == null || pathData.isBlank()) {
            return false;
        }

        String normalized = pathData.replace(",", " ").trim().replaceAll("\\s+", " ");
        return RECT_PATH_DATA_PATTERN.matcher(normalized).matches();
    }

    private record VectorizedSvgResult(String baseName, byte[] svgBytes) {
    }
}
