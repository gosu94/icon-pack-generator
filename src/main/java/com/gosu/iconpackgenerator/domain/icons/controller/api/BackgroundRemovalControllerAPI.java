package com.gosu.iconpackgenerator.domain.icons.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "Background Removal API", description = "Endpoints for removing background from images")
public interface BackgroundRemovalControllerAPI {

    @Operation(summary = "Remove background from an image", description = "Upload an image to have its background removed.")
    @PostMapping("/background-removal/process")
    @ResponseBody
    ResponseEntity<Map<String, Object>> processBackgroundRemoval(@RequestParam("image") MultipartFile imageFile);

    @Operation(summary = "Download processed image", description = "Downloads the image with the background removed.")
    @PostMapping("/background-removal/download")
    ResponseEntity<byte[]> downloadProcessedImage(@RequestParam("imageData") String base64ImageData,
                                                  @RequestParam("filename") String originalFilename);
}
