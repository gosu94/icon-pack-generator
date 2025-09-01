package com.gosu.iconpackgenerator.domain.controller;

import com.gosu.iconpackgenerator.domain.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.controller.api.IconExportControllerAPI;
import com.gosu.iconpackgenerator.domain.dto.GalleryExportRequest;
import com.gosu.iconpackgenerator.domain.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.service.FileStorageService;
import com.gosu.iconpackgenerator.domain.service.IconExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IconExportController implements IconExportControllerAPI {

    private final IconExportService iconExportService;
    private final StreamingStateStore streamingStateStore;
    private final GeneratedIconRepository generatedIconRepository;
    private final FileStorageService fileStorageService;

    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportIcons(@RequestBody IconExportRequest exportRequest) {
        log.info("Received export request for service: {}, generation: {} - creating comprehensive icon pack",
                exportRequest.getServiceName(), exportRequest.getGenerationIndex());

        List<IconGenerationResponse.GeneratedIcon> iconsToExport = exportRequest.getIcons();
        log.info("Received export request with {} icons in the request body.", (iconsToExport != null ? iconsToExport.size() : 0));

        // If icons are not provided in the request, fall back to fetching from storage
        if (iconsToExport == null || iconsToExport.isEmpty()) {
            log.info("No icons in request body, fetching from stored results for request ID: {}", exportRequest.getRequestId());
            IconGenerationResponse generationResponse = streamingStateStore.getResponse(exportRequest.getRequestId());
            if (generationResponse == null) {
                log.error("No generation results found for request ID: {}", exportRequest.getRequestId());
                return ResponseEntity.notFound().build();
            }

            iconsToExport = new ArrayList<>();
            List<IconGenerationResponse.ServiceResults> serviceResults = switch (exportRequest.getServiceName().toLowerCase()) {
                case "flux" -> generationResponse.getFalAiResults();
                case "recraft" -> generationResponse.getRecraftResults();
                case "photon" -> generationResponse.getPhotonResults();
                case "gpt" -> generationResponse.getGptResults();
                case "imagen" -> generationResponse.getImagenResults();
                default -> null;
            };

            if (serviceResults != null) {
                for (IconGenerationResponse.ServiceResults result : serviceResults) {
                    if (result.getGenerationIndex() == exportRequest.getGenerationIndex()) {
                        iconsToExport.addAll(result.getIcons());
                        break;
                    }
                }
            }
        }

        if (iconsToExport.isEmpty()) {
            log.error("No icons found for service: {} and generation index: {}", exportRequest.getServiceName(), exportRequest.getGenerationIndex());
            return ResponseEntity.notFound().build();
        }

        exportRequest.setIcons(iconsToExport);

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
    public ResponseEntity<byte[]> exportFromGallery(@RequestBody GalleryExportRequest galleryExportRequest) {
        log.info("Received gallery export request for {} icons.", galleryExportRequest.getIconFilePaths().size());

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
}
