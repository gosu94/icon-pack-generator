package com.gosu.iconpackgenerator.domain.icons.controller;

import com.gosu.iconpackgenerator.domain.icons.controller.api.BackgroundRemovalControllerAPI;
import com.gosu.iconpackgenerator.domain.icons.service.BackgroundRemovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BackgroundRemovalController implements BackgroundRemovalControllerAPI {

    private final BackgroundRemovalService backgroundRemovalService;

    @Override
    @ResponseBody
    public ResponseEntity<Map<String, Object>> processBackgroundRemoval(@RequestParam("image") MultipartFile imageFile) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Validate file
            if (imageFile.isEmpty()) {
                response.put("success", false);
                response.put("error", "No file uploaded");
                return ResponseEntity.badRequest().body(response);
            }

            // Check file type
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                response.put("success", false);
                response.put("error", "File must be an image");
                return ResponseEntity.badRequest().body(response);
            }

            // Check file size (limit to 10MB)
            if (imageFile.getSize() > 10 * 1024 * 1024) {
                response.put("success", false);
                response.put("error", "File size must be less than 10MB");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Processing background removal for file: {}, size: {} bytes, type: {}",
                    imageFile.getOriginalFilename(), imageFile.getSize(), contentType);

            // Convert to byte array
            byte[] originalImageData = imageFile.getBytes();

            // Process with background removal
            byte[] processedImageData = backgroundRemovalService.removeBackground(originalImageData);

            // Convert both images to base64 for JSON response
            String originalBase64 = Base64.getEncoder().encodeToString(originalImageData);
            String processedBase64 = Base64.getEncoder().encodeToString(processedImageData);

            // Prepare response
            response.put("success", true);
            response.put("originalImage", "data:" + contentType + ";base64," + originalBase64);
            response.put("processedImage", "data:" + contentType + ";base64," + processedBase64);
            response.put("originalSize", originalImageData.length);
            response.put("processedSize", processedImageData.length);
            response.put("filename", imageFile.getOriginalFilename());
            response.put("rembgAvailable", backgroundRemovalService.isRembgAvailable());

            // Add processing stats
            boolean wasProcessed = !java.util.Arrays.equals(originalImageData, processedImageData);
            response.put("backgroundRemoved", wasProcessed);

            if (wasProcessed) {
                double reductionPercent = 100.0 * (originalImageData.length - processedImageData.length) / originalImageData.length;
                response.put("sizeReduction", String.format("%.1f%%", reductionPercent));
            } else {
                response.put("sizeReduction", "0%");
                if (!backgroundRemovalService.isRembgAvailable()) {
                    response.put("message", "rembg not available - original image returned");
                } else {
                    response.put("message", "Background removal completed");
                }
            }

            log.info("Background removal completed. Original: {} bytes, Processed: {} bytes, Background removed: {}",
                    originalImageData.length, processedImageData.length, wasProcessed);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error processing uploaded file", e);
            response.put("success", false);
            response.put("error", "Error reading uploaded file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (Exception e) {
            log.error("Unexpected error during background removal", e);
            response.put("success", false);
            response.put("error", "Background removal failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @Override
    @ResponseBody
    public ResponseEntity<byte[]> downloadProcessedImage(@RequestParam("imageData") String base64ImageData,
                                                         @RequestParam("filename") String originalFilename) {
        try {
            // Remove data URL prefix if present
            String base64Data = base64ImageData;
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }

            // Decode base64 to bytes
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);

            // Determine filename
            String downloadFilename = originalFilename;
            if (downloadFilename != null && downloadFilename.contains(".")) {
                String nameWithoutExt = downloadFilename.substring(0, downloadFilename.lastIndexOf('.'));
                String ext = downloadFilename.substring(downloadFilename.lastIndexOf('.'));
                downloadFilename = nameWithoutExt + "_no_bg" + ext;
            } else {
                downloadFilename = "processed_image.png";
            }

            // Set response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", downloadFilename);
            headers.setContentLength(imageBytes.length);

            log.info("Downloading processed image: {} ({} bytes)", downloadFilename, imageBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);

        } catch (Exception e) {
            log.error("Error preparing image download", e);
            return ResponseEntity.status(500).build();
        }
    }
}
