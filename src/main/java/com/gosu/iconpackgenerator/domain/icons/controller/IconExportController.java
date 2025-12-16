package com.gosu.iconpackgenerator.domain.icons.controller;

import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.controller.api.IconExportControllerAPI;
import com.gosu.iconpackgenerator.domain.icons.dto.GalleryExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.GifGalleryExportRequest;
import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.util.FileStorageService;
import com.gosu.iconpackgenerator.domain.icons.service.IconExportService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IconExportController implements IconExportControllerAPI {

    private final IconExportService iconExportService;
    private final StreamingStateStore streamingStateStore;
    private final GeneratedIconRepository generatedIconRepository;
    private final FileStorageService fileStorageService;
    private final CoinManagementService coinManagementService;

    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportIcons(@RequestBody IconExportRequest exportRequest,
                                              @AuthenticationPrincipal OAuth2User principal) {
        String requestedService = exportRequest.getServiceName() != null ?
                exportRequest.getServiceName().toLowerCase() : "gpt";
        exportRequest.setServiceName(requestedService);

        log.info("Received export request for service: {} from request: {} - creating comprehensive icon pack with all generations",
                requestedService, exportRequest.getRequestId());

        if (!(principal instanceof CustomOAuth2User customUser)) {
            log.warn("Unauthorized export attempt: no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not authenticated".getBytes(StandardCharsets.UTF_8));
        }

        User user = customUser.getUser();

        List<IconGenerationResponse.GeneratedIcon> iconsToExport = exportRequest.getIcons();
        log.info("Received export request with {} icons in the request body.", (iconsToExport != null ? iconsToExport.size() : 0));

        // If icons are not provided in the request, fall back to fetching ALL generations from storage
        if (iconsToExport == null || iconsToExport.isEmpty()) {
            log.info("No icons in request body, fetching all generations from stored results for request ID: {}", exportRequest.getRequestId());
            IconGenerationResponse generationResponse = streamingStateStore.getResponse(exportRequest.getRequestId());
            if (generationResponse == null) {
                log.error("No generation results found for request ID: {}", exportRequest.getRequestId());
                return ResponseEntity.notFound().build();
            }

            iconsToExport = new ArrayList<>();
            List<IconGenerationResponse.ServiceResults> serviceResults = switch (requestedService) {
                case "gpt15" -> generationResponse.getGpt15Results();
                case "gpt" -> generationResponse.getGptResults();
                default -> generationResponse.getGptResults();
            };

            if (serviceResults != null) {
                // Export ALL icons from the specific generation index (all batches of that generation)
                for (IconGenerationResponse.ServiceResults result : serviceResults) {
                    if (result.getGenerationIndex() == exportRequest.getGenerationIndex() && 
                        result.getIcons() != null && !result.getIcons().isEmpty()) {
                        iconsToExport.addAll(result.getIcons());
                        log.info("Added {} icons from generation {} batch for service {}", 
                                result.getIcons().size(), result.getGenerationIndex(), exportRequest.getServiceName());
                    }
                }
            }
        }

        if (iconsToExport.isEmpty()) {
            log.error("No icons found for service: {} in request: {}", requestedService, exportRequest.getRequestId());
            return ResponseEntity.notFound().build();
        }

        exportRequest.setIcons(iconsToExport);

        int iconCount = Math.max(iconsToExport.size(), 1);
        int vectorCoinCost = exportRequest.isVectorizeSvg() ? (int) Math.ceil(iconCount / 9.0) : 0;
        int hqCoinCost = exportRequest.isHqUpscale() ? (int) Math.ceil(iconCount / 9.0) : 0;
        int totalCoinCost = vectorCoinCost + hqCoinCost;

        if (totalCoinCost > 0) {
            CoinManagementService.CoinDeductionResult coinResult = coinManagementService.deductCoinsForGeneration(user, totalCoinCost);
            if (!coinResult.isSuccess()) {
                log.warn("Insufficient coins for premium export options by user {}: {}", user.getEmail(), coinResult.getErrorMessage());
                String errorMessage = coinResult.getErrorMessage() != null
                        ? coinResult.getErrorMessage()
                        : "Insufficient coins for premium export options.";
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(errorMessage.getBytes(StandardCharsets.UTF_8));
            }
            log.info("Export coin deduction for user {}: deducted {} {} coin(s) (total cost: {}, vector: {}, hq: {}, icon count: {})",
                    user.getEmail(),
                    coinResult.getDeductedAmount(),
                    coinResult.isUsedTrialCoins() ? "trial" : "regular",
                    totalCoinCost,
                    vectorCoinCost,
                    hqCoinCost,
                    iconsToExport.size());
        }

        try {
            byte[] zipData = iconExportService.createIconPackZip(exportRequest);

            String fileName = "icon-pack-" + exportRequest.getRequestId() + "-" + exportRequest.getServiceName() + "-gen" + exportRequest.getGenerationIndex() + ".zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            log.info("Successfully created ZIP file: {} ({} bytes)", fileName, zipData.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);

        } catch (Exception e) {
            log.error("Error creating icon pack export", e);
            return ResponseEntity.internalServerError()
                    .body("Error creating icon pack".getBytes());
        }
    }

    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportFromGallery(@RequestBody GalleryExportRequest galleryExportRequest,
                                                    @AuthenticationPrincipal OAuth2User principal) {
        log.info("Received gallery export request for {} icons.", galleryExportRequest.getIconFilePaths().size());

        if (!(principal instanceof CustomOAuth2User customUser)) {
            log.warn("Unauthorized gallery export attempt: no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not authenticated".getBytes(StandardCharsets.UTF_8));
        }

        User user = customUser.getUser();

        try {
            List<IconGenerationResponse.GeneratedIcon> iconsToExport = new ArrayList<>();
            List<GeneratedIcon> foundIcons = generatedIconRepository.findByFilePathIn(galleryExportRequest.getIconFilePaths());

            for (GeneratedIcon generatedIcon : foundIcons) {
                IconGenerationResponse.GeneratedIcon iconDto = new IconGenerationResponse.GeneratedIcon();
                iconDto.setId(generatedIcon.getIconId());

                try {
                    byte[] iconData = fileStorageService.readIcon(generatedIcon.getFilePath());
                    iconDto.setBase64Data(Base64.getEncoder().encodeToString(iconData));
                } catch (IOException e) {
                    log.error("Error reading icon file: {}", generatedIcon.getFilePath(), e);
                    continue; // Skip this icon if file can't be read
                }

                iconDto.setDescription(generatedIcon.getDescription());
                iconDto.setGridPosition(generatedIcon.getGridPosition());
                iconDto.setServiceSource(generatedIcon.getServiceSource());
                iconsToExport.add(iconDto);
            }

            if (iconsToExport.isEmpty()) {
                log.error("No icons found for the given file paths.");
                return ResponseEntity.notFound().build();
            }

            IconExportRequest exportRequest = new IconExportRequest();
            exportRequest.setIcons(iconsToExport);
            exportRequest.setRequestId("gallery-export-" + UUID.randomUUID().toString().substring(0, 8));
            exportRequest.setServiceName("gallery");
            exportRequest.setGenerationIndex(1);
            exportRequest.setFormats(galleryExportRequest.getFormats()); // Pass formats from gallery request
            exportRequest.setVectorizeSvg(galleryExportRequest.isVectorizeSvg());
            exportRequest.setHqUpscale(galleryExportRequest.isHqUpscale());

            int iconCount = Math.max(iconsToExport.size(), 1);
            int galleryVectorCost = exportRequest.isVectorizeSvg() ? (int) Math.ceil(iconCount / 9.0) : 0;
            int galleryHqCost = exportRequest.isHqUpscale() ? (int) Math.ceil(iconCount / 9.0) : 0;
            int galleryTotalCost = galleryVectorCost + galleryHqCost;

            if (galleryTotalCost > 0) {
                CoinManagementService.CoinDeductionResult coinResult = coinManagementService.deductCoinsForGeneration(user, galleryTotalCost);
                if (!coinResult.isSuccess()) {
                    log.warn("Insufficient coins for gallery premium export options by user {}: {}", user.getEmail(), coinResult.getErrorMessage());
                    String errorMessage = coinResult.getErrorMessage() != null
                            ? coinResult.getErrorMessage()
                            : "Insufficient coins for premium export options.";
                    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                            .body(errorMessage.getBytes(StandardCharsets.UTF_8));
                }
                log.info("Gallery export coin deduction for user {}: deducted {} {} coin(s) (total cost: {}, vector: {}, hq: {}, icon count: {})",
                        user.getEmail(),
                        coinResult.getDeductedAmount(),
                        coinResult.isUsedTrialCoins() ? "trial" : "regular",
                        galleryTotalCost,
                        galleryVectorCost,
                        galleryHqCost,
                        iconsToExport.size());
            }

            byte[] zipData = iconExportService.createIconPackZip(exportRequest);

            String fileName = "icon-pack-gallery.zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            log.info("Successfully created ZIP file from gallery export: {} ({} bytes)", fileName, zipData.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);

        } catch (Exception e) {
            log.error("Error creating icon pack export from gallery", e);
            return ResponseEntity.internalServerError()
                    .body("Error creating icon pack".getBytes());
        }
    }

    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportGifsFromGallery(@RequestBody GifGalleryExportRequest gifExportRequest,
                                                        @AuthenticationPrincipal OAuth2User principal) {
        if (gifExportRequest.getGifFilePaths() == null || gifExportRequest.getGifFilePaths().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("No GIF file paths provided".getBytes(StandardCharsets.UTF_8));
        }

        if (!(principal instanceof CustomOAuth2User customUser)) {
            log.warn("Unauthorized GIF export attempt: no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not authenticated".getBytes(StandardCharsets.UTF_8));
        }

        User user = customUser.getUser();
        List<GeneratedIcon> generatedGifs = generatedIconRepository.findByFilePathIn(gifExportRequest.getGifFilePaths());
        List<GeneratedIcon> userGifs = generatedGifs.stream()
                .filter(icon -> icon.getUser().getId().equals(user.getId()))
                .filter(icon -> icon.getFileName() != null && icon.getFileName().toLowerCase().endsWith(".gif"))
                .toList();

        if (userGifs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            if (userGifs.size() == 1) {
                GeneratedIcon gifIcon = userGifs.get(0);
                byte[] gifData = fileStorageService.readIcon(gifIcon.getFilePath());
                String fileName = gifIcon.getFileName() != null ? gifIcon.getFileName() : "gallery-animation.gif";

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_GIF);
                headers.setContentDispositionFormData("attachment", fileName);
                headers.setContentLength(gifData.length);
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(gifData);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (GeneratedIcon gifIcon : userGifs) {
                    try {
                        byte[] gifData = fileStorageService.readIcon(gifIcon.getFilePath());
                        String entryName = gifIcon.getFileName();
                        if (entryName == null || entryName.isBlank()) {
                            entryName = gifIcon.getIconId() + ".gif";
                        }
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.write(gifData);
                        zos.closeEntry();
                    } catch (IOException e) {
                        log.error("Error reading GIF file {} for export", gifIcon.getFilePath(), e);
                    }
                }
                zos.finish();
            }

            byte[] zipData = baos.toByteArray();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    "gallery-gifs-" + System.currentTimeMillis() + ".zip");
            headers.setContentLength(zipData.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);

        } catch (IOException e) {
            log.error("Failed to export GIFs from gallery", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to export GIFs".getBytes(StandardCharsets.UTF_8));
        }
    }
}
