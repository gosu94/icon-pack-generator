package com.gosu.iconpackgenerator.domain.mockups.controller;

import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.icons.service.IconExportService;
import com.gosu.iconpackgenerator.util.FileStorageService;
import com.gosu.iconpackgenerator.domain.mockups.controller.api.MockupExportControllerAPI;
import com.gosu.iconpackgenerator.domain.mockups.dto.GalleryMockupExportRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupExportRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupElementExportRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse;
import com.gosu.iconpackgenerator.domain.mockups.entity.GeneratedMockup;
import com.gosu.iconpackgenerator.domain.mockups.repository.GeneratedMockupRepository;
import com.gosu.iconpackgenerator.domain.mockups.service.MockupExportService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
public class MockupExportController implements MockupExportControllerAPI {
    
    private final MockupExportService mockupExportService;
    private final IconExportService iconExportService;
    private final CoinManagementService coinManagementService;
    private final StreamingStateStore streamingStateStore;
    private final GeneratedMockupRepository generatedMockupRepository;
    private final FileStorageService fileStorageService;
    
    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportMockups(@RequestBody MockupExportRequest exportRequest) {
        log.info("Received export request for mockups from request: {} with {} mockups in request body",
            exportRequest.getRequestId(),
            exportRequest.getMockups() != null ? exportRequest.getMockups().size() : 0);
        
        List<MockupGenerationResponse.GeneratedMockup> mockupsToExport = exportRequest.getMockups();
        
        // If mockups are not provided in request, fetch from stored response
        if (mockupsToExport == null || mockupsToExport.isEmpty()) {
            log.info("No mockups in request body, fetching from stored results for request ID: {}", 
                    exportRequest.getRequestId());
            
            try {
                MockupGenerationResponse generationResponse = streamingStateStore.getResponse(exportRequest.getRequestId());
                if (generationResponse == null) {
                    log.error("No generation results found for request ID: {}", exportRequest.getRequestId());
                    return ResponseEntity.notFound().build();
                }
                
                mockupsToExport = new ArrayList<>();
                List<MockupGenerationResponse.ServiceResults> bananaResults = generationResponse.getBananaResults();
                
                if (bananaResults != null) {
                    for (MockupGenerationResponse.ServiceResults result : bananaResults) {
                        boolean includeAll = exportRequest.getGenerationIndex() <= 0;
                        if ((includeAll || result.getGenerationIndex() == exportRequest.getGenerationIndex()) &&
                                result.getMockups() != null && !result.getMockups().isEmpty()) {
                            mockupsToExport.addAll(result.getMockups());
                            log.info("Added {} mockups from generation {} batch",
                                    result.getMockups().size(), result.getGenerationIndex());
                        }
                    }
                }
            } catch (ClassCastException e) {
                log.error("Type mismatch when retrieving stored response - expected MockupGenerationResponse", e);
                return ResponseEntity.badRequest().body("Invalid request type".getBytes());
            }
        }
        
        if (mockupsToExport.isEmpty()) {
            log.error("No mockups found for request: {}", exportRequest.getRequestId());
            return ResponseEntity.notFound().build();
        }
        
        exportRequest.setMockups(mockupsToExport);
        
        try {
            byte[] zipData = mockupExportService.createMockupPackZip(exportRequest);
            
            String fileName = "mockup-pack-" + exportRequest.getRequestId() + "-gen" + 
                exportRequest.getGenerationIndex() + ".zip";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);
            
            log.info("Successfully created mockup ZIP file: {} ({} bytes)", fileName, zipData.length);
            return ResponseEntity.ok()
                .headers(headers)
                .body(zipData);
            
        } catch (Exception e) {
            log.error("Error creating mockup pack export", e);
            return ResponseEntity.internalServerError()
                .body("Error creating mockup pack".getBytes());
        }
    }

    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportMockupElements(@RequestBody MockupElementExportRequest exportRequest,
                                                       @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body("User not authenticated".getBytes());
        }

        User user = customUser.getUser();
        MockupGenerationResponse generationResponse = streamingStateStore.getResponse(exportRequest.getRequestId());
        if (generationResponse == null || generationResponse.getElements() == null || generationResponse.getElements().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<IconGenerationResponse.GeneratedIcon> elementsToExport = generationResponse.getElements();
        int elementCount = Math.max(elementsToExport.size(), 1);
        int vectorCoinCost = exportRequest.isVectorizeSvg() ? (int) Math.ceil(elementCount / 9.0) : 0;
        int hqCoinCost = exportRequest.isHqUpscale() ? (int) Math.ceil(elementCount / 9.0) : 0;
        int totalCoinCost = vectorCoinCost + hqCoinCost;

        if (totalCoinCost > 0) {
            CoinManagementService.CoinDeductionResult coinResult =
                    coinManagementService.deductCoinsForGeneration(user, totalCoinCost);
            if (!coinResult.isSuccess()) {
                String errorMessage = coinResult.getErrorMessage() != null
                        ? coinResult.getErrorMessage()
                        : "Insufficient coins for premium export options.";
                return ResponseEntity.status(402).body(errorMessage.getBytes());
            }
        }

        IconExportRequest iconExportRequest = new IconExportRequest();
        iconExportRequest.setIcons(elementsToExport);
        iconExportRequest.setRequestId("mockup-elements-" + exportRequest.getRequestId());
        iconExportRequest.setServiceName("mockup-elements");
        iconExportRequest.setGenerationIndex(1);
        iconExportRequest.setFormats(exportRequest.getFormats());
        iconExportRequest.setVectorizeSvg(exportRequest.isVectorizeSvg());
        iconExportRequest.setHqUpscale(exportRequest.isHqUpscale());
        iconExportRequest.setMinSvgSize(256);

        byte[] zipData = iconExportService.createIconPackZip(iconExportRequest);
        String fileName = "ui-elements-pack-" + exportRequest.getRequestId() + ".zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(zipData.length);

        return ResponseEntity.ok().headers(headers).body(zipData);
    }

    /**
     * Export mockups from the gallery
     */
    @PostMapping("/export-gallery")
    @ResponseBody
    public ResponseEntity<byte[]> exportFromGallery(@RequestBody GalleryMockupExportRequest galleryExportRequest) {
        log.info("Received gallery export request for {} mockups.", galleryExportRequest.getMockupFilePaths().size());

        try {
            List<MockupGenerationResponse.GeneratedMockup> mockupsToExport = new ArrayList<>();
            List<GeneratedMockup> foundMockups = generatedMockupRepository
                    .findByFilePathIn(galleryExportRequest.getMockupFilePaths());

            for (GeneratedMockup generatedMockup : foundMockups) {
                MockupGenerationResponse.GeneratedMockup mockupDto = 
                        new MockupGenerationResponse.GeneratedMockup();
                mockupDto.setId(generatedMockup.getMockupId());

                try {
                    byte[] mockupData = fileStorageService.readMockup(generatedMockup.getFilePath());
                    mockupDto.setBase64Data(Base64.getEncoder().encodeToString(mockupData));
                } catch (IOException e) {
                    log.error("Error reading mockup file: {}", generatedMockup.getFilePath(), e);
                    continue; // Skip this mockup if file can't be read
                }

                mockupDto.setDescription(generatedMockup.getDescription());
                mockupsToExport.add(mockupDto);
            }

            if (mockupsToExport.isEmpty()) {
                log.error("No mockups found for the given file paths.");
                return ResponseEntity.notFound().build();
            }

            MockupExportRequest exportRequest = new MockupExportRequest();
            exportRequest.setMockups(mockupsToExport);
            exportRequest.setRequestId("gallery-export-" + UUID.randomUUID().toString().substring(0, 8));
            exportRequest.setServiceName("gallery");
            exportRequest.setGenerationIndex(1);
            exportRequest.setFormats(galleryExportRequest.getFormats());
            exportRequest.setSizes(galleryExportRequest.getSizes());

            byte[] zipData = mockupExportService.createMockupPackZip(exportRequest);

            String fileName = exportRequest.getRequestId() + "-gallery-mockups.zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            log.info("Successfully created gallery mockup ZIP file: {} ({} bytes)", fileName, zipData.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);

        } catch (Exception e) {
            log.error("Error exporting mockups from gallery", e);
            return ResponseEntity.internalServerError()
                    .body("Error creating mockup pack".getBytes());
        }
    }
}
