package com.gosu.iconpackgenerator.domain.illustrations.controller;

import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.illustrations.controller.api.IllustrationExportControllerAPI;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationExportRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IllustrationExportController implements IllustrationExportControllerAPI {
    
    private final IllustrationExportService illustrationExportService;
    private final StreamingStateStore streamingStateStore;
    
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
}

