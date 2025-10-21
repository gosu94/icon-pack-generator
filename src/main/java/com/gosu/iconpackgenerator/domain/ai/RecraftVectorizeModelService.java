package com.gosu.iconpackgenerator.domain.ai;

import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import ai.fal.client.queue.QueueStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.iconpackgenerator.exception.FalAiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecraftVectorizeModelService {

    private static final String RECRAFT_VECTORIZE_ENDPOINT = "fal-ai/recraft/vectorize";
    private static final String DEFAULT_IMAGE_MEDIA_TYPE = "image/png";
    private static final String JPEG_IMAGE_MEDIA_TYPE = "image/jpeg";
    private static final String WEBP_IMAGE_MEDIA_TYPE = "image/webp";

    private final FalClient falClient;
    private final ObjectMapper objectMapper;

    /**
     * Vectorize a PNG/JPEG/WEBP image and return the generated SVG content.
     *
     * @param imageData raster image bytes
     * @return asynchronous SVG bytes
     */
    public CompletableFuture<byte[]> vectorizeImage(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> vectorizeImageBlocking(imageData));
    }

    /**
     * Vectorize an image that is already accessible via URL or data URI.
     *
     * @param imageUrl a public URL or data URI pointing to the image
     * @return asynchronous SVG bytes
     */
    public CompletableFuture<byte[]> vectorizeImage(String imageUrl) {
        return CompletableFuture.supplyAsync(() -> vectorizeImageBlocking(imageUrl));
    }

    /**
     * Blocking vectorization helper for byte arrays.
     *
     * @param imageData raster image bytes
     * @return SVG bytes
     */
    public byte[] vectorizeImageBlocking(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            throw new FalAiException("Image data cannot be empty for vectorization.");
        }

        String mediaType = resolveMediaType(imageData);
        String imageDataUri = convertToDataUri(imageData, mediaType);
        return vectorizeImageBlocking(imageDataUri);
    }

    /**
     * Blocking vectorization helper for accessible image URLs or data URIs.
     *
     * @param imageUrl a public URL or data URI pointing to the image
     * @return SVG bytes
     */
    public byte[] vectorizeImageBlocking(String imageUrl) {
        log.info("Vectorizing image using Recraft vectorize endpoint");
        return executeVectorization(imageUrl);
    }

    private Map<String, Object> createInput(String imageUrl) {
        Map<String, Object> input = new HashMap<>();
        input.put("image_url", imageUrl);
        return input;
    }

    private byte[] executeVectorization(String imageUrl) {
        try {
            Map<String, Object> input = createInput(imageUrl);

            Output<JsonObject> output = falClient.subscribe(RECRAFT_VECTORIZE_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                            .input(input)
                            .logs(true)
                            .resultType(JsonObject.class)
                            .onQueueUpdate(update -> {
                                if (update instanceof QueueStatus.InProgress inProgress) {
                                    log.debug("Recraft vectorize progress: {}", inProgress.getLogs());
                                }
                            })
                            .build()
            );

            JsonObject result = output.getData();
            JsonNode jsonResult = objectMapper.readTree(result.toString());

            return extractSvgFromResult(jsonResult);
        } catch (ai.fal.client.exception.FalException e) {
            log.error("Recraft vectorize API error: {}", e.getMessage());
            throw new FalAiException("Recraft vectorize service failed to process the image.", e);
        } catch (Exception e) {
            log.error("Unexpected error while vectorizing image", e);
            throw new FalAiException("Failed to vectorize image with Recraft vectorize service.", e);
        }
    }

    private byte[] extractSvgFromResult(JsonNode result) {
        JsonNode imageNode = result.path("image");
        if (!imageNode.isMissingNode()) {
            String svgUrl = imageNode.path("url").asText();
            if (!svgUrl.isEmpty()) {
                log.info("Downloading vectorized SVG from {}", svgUrl);
                return downloadSvg(svgUrl);
            }
        }

        log.error("Invalid response from Recraft vectorize endpoint: {}", result);
        throw new FalAiException("Recraft vectorize response did not contain SVG data.");
    }

    private byte[] downloadSvg(String svgUrl) {
        try {
            URL url = URI.create(svgUrl).toURL();
            try (InputStream inputStream = url.openStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                byte[] svgBytes = outputStream.toByteArray();
                log.info("Successfully downloaded vectorized SVG ({} bytes)", svgBytes.length);
                return svgBytes;
            }
        } catch (IOException e) {
            log.error("Failed to download SVG from {}", svgUrl, e);
            throw new FalAiException("Failed to download vectorized SVG from fal.ai", e);
        }
    }

    private String convertToDataUri(byte[] imageData, String mediaType) {
        try {
            String base64Data = Base64.getEncoder().encodeToString(imageData);
            return "data:" + mediaType + ";base64," + base64Data;
        } catch (Exception e) {
            log.error("Error converting image to data URI", e);
            throw new FalAiException("Failed to convert image to data URI for vectorization.", e);
        }
    }

    private String resolveMediaType(byte[] imageData) {
        if (imageData == null || imageData.length < 4) {
            return DEFAULT_IMAGE_MEDIA_TYPE;
        }

        // PNG magic number: 89 50 4E 47
        if ((imageData[0] & 0xFF) == 0x89 &&
                imageData[1] == 'P' &&
                imageData[2] == 'N' &&
                imageData[3] == 'G') {
            return DEFAULT_IMAGE_MEDIA_TYPE;
        }

        // JPEG magic number: FF D8
        if ((imageData[0] & 0xFF) == 0xFF &&
                (imageData[1] & 0xFF) == 0xD8) {
            return JPEG_IMAGE_MEDIA_TYPE;
        }

        // WEBP magic number: RIFF....WEBP
        if (imageData.length >= 12 &&
                imageData[0] == 'R' &&
                imageData[1] == 'I' &&
                imageData[2] == 'F' &&
                imageData[3] == 'F' &&
                imageData[8] == 'W' &&
                imageData[9] == 'E' &&
                imageData[10] == 'B' &&
                imageData[11] == 'P') {
            return WEBP_IMAGE_MEDIA_TYPE;
        }

        return DEFAULT_IMAGE_MEDIA_TYPE;
    }
}
