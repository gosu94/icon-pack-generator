package com.gosu.iconpackgenerator.domain.illustrations.controller;

import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.service.FileStorageService;
import com.gosu.iconpackgenerator.domain.illustrations.controller.api.IllustrationExportControllerAPI;
import com.gosu.iconpackgenerator.domain.illustrations.dto.GalleryIllustrationExportRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationExportRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
import com.gosu.iconpackgenerator.domain.illustrations.entity.GeneratedIllustration;
import com.gosu.iconpackgenerator.domain.illustrations.repository.GeneratedIllustrationRepository;
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
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
public class IllustrationExportController implements IllustrationExportControllerAPI {
    
    private final IllustrationExportService illustrationExportService;
    private final StreamingStateStore streamingStateStore;
    private final GeneratedIllustrationRepository generatedIllustrationRepository;
    private final FileStorageService fileStorageService;
    
    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportIllustrations(@RequestBody IllustrationExportRequest exportRequest) {
        log.info("Received export request for illustrations from request: {} with {} illustrations in request body",
            exportRequest.getRequestId(),
            exportRequest.getIllustrations() != null ? exportRequest.getIllustrations().size() : 0);
        
        List<IllustrationGenerationResponse.GeneratedIllustration> illustrationsToExport = exportRequest.getIllustrations();
        
        // If illustrations are not provided in request, fetch from stored response
        if (illustrationsToExport == null || illustrationsToExport.isEmpty()) {
            log.info("No illustrations in request body, fetching from stored results for request ID: {}", 
                    exportRequest.getRequestId());
            
            try {
                IllustrationGenerationResponse generationResponse = streamingStateStore.getResponse(exportRequest.getRequestId());
                if (generationResponse == null) {
                    log.error("No generation results found for request ID: {}", exportRequest.getRequestId());
                    return ResponseEntity.notFound().build();
                }
                
                illustrationsToExport = new ArrayList<>();
                List<IllustrationGenerationResponse.ServiceResults> bananaResults = generationResponse.getBananaResults();
                
                if (bananaResults != null) {
                    for (IllustrationGenerationResponse.ServiceResults result : bananaResults) {
                        if (result.getGenerationIndex() == exportRequest.getGenerationIndex() &&
                            result.getIllustrations() != null && !result.getIllustrations().isEmpty()) {
                            illustrationsToExport.addAll(result.getIllustrations());
                            log.info("Added {} illustrations from generation {} batch",
                                    result.getIllustrations().size(), result.getGenerationIndex());
                        }
                    }
                }
            } catch (ClassCastException e) {
                log.error("Type mismatch when retrieving stored response - expected IllustrationGenerationResponse", e);
                return ResponseEntity.badRequest().body("Invalid request type".getBytes());
            }
        }
        
        if (illustrationsToExport.isEmpty()) {
            log.error("No illustrations found for request: {}", exportRequest.getRequestId());
            return ResponseEntity.notFound().build();
        }
        
        exportRequest.setIllustrations(illustrationsToExport);
        
        try {
            byte[] zipData = illustrationExportService.createIllustrationPackZip(exportRequest);
            
            String fileName = "illustration-pack-" + exportRequest.getRequestId() + "-gen" + 
                exportRequest.getGenerationIndex() + ".zip";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);
            
            log.info("Successfully created illustration ZIP file: {} ({} bytes)", fileName, zipData.length);
            return ResponseEntity.ok()
                .headers(headers)
                .body(zipData);
            
        } catch (Exception e) {
            log.error("Error creating illustration pack export", e);
            return ResponseEntity.internalServerError()
                .body("Error creating illustration pack".getBytes());
        }
    }

    /**
     * Export illustrations from the gallery
     */
    @PostMapping("/export-gallery")
    @ResponseBody
    public ResponseEntity<byte[]> exportFromGallery(@RequestBody GalleryIllustrationExportRequest galleryExportRequest) {
        log.info("Received gallery export request for {} illustrations.", galleryExportRequest.getIllustrationFilePaths().size());

        try {
            List<IllustrationGenerationResponse.GeneratedIllustration> illustrationsToExport = new ArrayList<>();
            List<GeneratedIllustration> foundIllustrations = generatedIllustrationRepository
                    .findByFilePathIn(galleryExportRequest.getIllustrationFilePaths());

            for (GeneratedIllustration generatedIllustration : foundIllustrations) {
                IllustrationGenerationResponse.GeneratedIllustration illustrationDto = 
                        new IllustrationGenerationResponse.GeneratedIllustration();
                illustrationDto.setId(generatedIllustration.getIllustrationId());

                try {
                    byte[] illustrationData = fileStorageService.readIllustration(generatedIllustration.getFilePath());
                    illustrationDto.setBase64Data(Base64.getEncoder().encodeToString(illustrationData));
                } catch (IOException e) {
                    log.error("Error reading illustration file: {}", generatedIllustration.getFilePath(), e);
                    continue; // Skip this illustration if file can't be read
                }

                illustrationDto.setDescription(generatedIllustration.getDescription());
                illustrationDto.setGridPosition(generatedIllustration.getGridPosition());
                illustrationsToExport.add(illustrationDto);
            }

            if (illustrationsToExport.isEmpty()) {
                log.error("No illustrations found for the given file paths.");
                return ResponseEntity.notFound().build();
            }

            IllustrationExportRequest exportRequest = new IllustrationExportRequest();
            exportRequest.setIllustrations(illustrationsToExport);
            exportRequest.setRequestId("gallery-export-" + UUID.randomUUID().toString().substring(0, 8));
            exportRequest.setServiceName("gallery");
            exportRequest.setGenerationIndex(1);
            exportRequest.setFormats(galleryExportRequest.getFormats());
            exportRequest.setSizes(galleryExportRequest.getSizes());

            byte[] zipData = illustrationExportService.createIllustrationPackZip(exportRequest);

            String fileName = exportRequest.getRequestId() + "-gallery-illustrations.zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            log.info("Successfully created gallery illustration ZIP file: {} ({} bytes)", fileName, zipData.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);

        } catch (Exception e) {
            log.error("Error exporting illustrations from gallery", e);
            return ResponseEntity.internalServerError()
                    .body("Error creating illustration pack".getBytes());
        }
    }
}

