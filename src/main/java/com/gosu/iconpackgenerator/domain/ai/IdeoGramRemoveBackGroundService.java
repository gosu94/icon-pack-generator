package com.gosu.iconpackgenerator.domain.ai;

import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.iconpackgenerator.exception.FalAiException;
import com.gosu.iconpackgenerator.util.ErrorMessageSanitizer;
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
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdeoGramRemoveBackGroundService {

    private static final String IDEOGRAM_REMOVE_BACKGROUND_ENDPOINT = "fal-ai/ideogram/remove-background";
    private static final int OUTPUT_SIZE = 1024;

    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    private final ErrorMessageSanitizer errorMessageSanitizer;

    public byte[] removeBackground(byte[] imageData) {
        try {
            if (imageData == null || imageData.length == 0) {
                throw new FalAiException("Cannot remove background from empty image data");
            }

            String imageDataUrl = convertToDataUrl(imageData);
            Map<String, Object> input = createInputMap(imageDataUrl);

            log.info("Removing image background with Ideogram fal.ai endpoint: {}", IDEOGRAM_REMOVE_BACKGROUND_ENDPOINT);
            Output<JsonObject> output = falClient.subscribe(IDEOGRAM_REMOVE_BACKGROUND_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                            .input(input)
                            .logs(true)
                            .resultType(JsonObject.class)
                            .build()
            );

            JsonObject result = output.getData();
            JsonNode jsonResult = objectMapper.readTree(result.toString());
            logIdeogramOutputImage(jsonResult);
            byte[] transparentImageData = extractImageFromResult(jsonResult);
            return normalizeTo1024Png(transparentImageData);
        } catch (Exception e) {
            log.error("Error removing background with Ideogram fal.ai API", e);
            String sanitizedError = errorMessageSanitizer.sanitizeErrorMessage(e.getMessage(), "IdeogramRemoveBackground");
            throw new FalAiException(sanitizedError, e);
        }
    }

    private Map<String, Object> createInputMap(String imageDataUrl) {
        Map<String, Object> input = new HashMap<>();
        input.put("image_url", imageDataUrl);
        return input;
    }

    private String convertToDataUrl(byte[] imageData) {
        String base64Data = Base64.getEncoder().encodeToString(imageData);
        return "data:image/png;base64," + base64Data;
    }

    private byte[] extractImageFromResult(JsonNode result) {
        JsonNode imageNode = result.path("image");
        if (!imageNode.isMissingNode()) {
            String imageUrl = imageNode.path("url").asText();
            if (!imageUrl.isEmpty()) {
                if (imageUrl.startsWith("data:image/")) {
                    log.debug("Found Ideogram remove-background data URL response");
                    return decodeDataUrl(imageUrl);
                }

                log.info("Downloading Ideogram background-removed image from fal.ai URL: {}", imageUrl);
                return downloadImageFromUrl(imageUrl);
            }

            String base64Data = imageNode.path("base64").asText();
            if (!base64Data.isEmpty()) {
                log.debug("Found Ideogram remove-background base64 response");
                return Base64.getDecoder().decode(base64Data);
            }
        }

        log.error("Could not extract image data from Ideogram remove-background result: {}", result);
        throw new FalAiException("Invalid response format from Ideogram remove-background API - no image found");
    }

    private void logIdeogramOutputImage(JsonNode result) {
        JsonNode imageNode = result.path("image");
        if (imageNode.isMissingNode()) {
            log.warn("Ideogram remove-background response did not contain image node: {}", result);
            return;
        }

        String imageUrl = imageNode.path("url").asText();
        if (!imageUrl.isEmpty()) {
            log.info("Ideogram remove-background output image URL: {}", imageUrl);
            return;
        }

        String base64Data = imageNode.path("base64").asText();
        if (!base64Data.isEmpty()) {
            log.info("Ideogram remove-background output image returned as base64 data ({} chars)", base64Data.length());
            return;
        }

        log.warn("Ideogram remove-background image node did not contain url or base64 data: {}", imageNode);
    }

    private byte[] decodeDataUrl(String dataUrl) {
        int commaIndex = dataUrl.indexOf(',');
        if (commaIndex < 0 || commaIndex == dataUrl.length() - 1) {
            throw new FalAiException("Invalid data URL returned by Ideogram remove-background API");
        }
        return Base64.getDecoder().decode(dataUrl.substring(commaIndex + 1));
    }

    private byte[] downloadImageFromUrl(String imageUrl) {
        try {
            URL url = URI.create(imageUrl).toURL();
            try (InputStream inputStream = url.openStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            log.error("Failed to download Ideogram background-removed image from URL: {}", imageUrl, e);
            throw new FalAiException("Failed to download Ideogram background-removed image from URL: " + imageUrl, e);
        }
    }

    private byte[] normalizeTo1024Png(byte[] imageData) {
        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (sourceImage == null) {
                throw new FalAiException("Failed to parse Ideogram background-removed image");
            }

            BufferedImage targetImage = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = targetImage.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(sourceImage, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(targetImage, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Failed to normalize Ideogram background-removed image to {}x{}", OUTPUT_SIZE, OUTPUT_SIZE, e);
            throw new FalAiException("Failed to normalize Ideogram background-removed image", e);
        }
    }
}
