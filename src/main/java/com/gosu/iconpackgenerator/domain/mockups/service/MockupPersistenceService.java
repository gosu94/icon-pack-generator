package com.gosu.iconpackgenerator.domain.mockups.service;

import com.gosu.iconpackgenerator.util.FileStorageService;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse;
import com.gosu.iconpackgenerator.domain.mockups.entity.GeneratedMockup;
import com.gosu.iconpackgenerator.domain.mockups.repository.GeneratedMockupRepository;
import com.gosu.iconpackgenerator.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for persisting generated mockups to database and file system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MockupPersistenceService {
    
    private final GeneratedMockupRepository generatedMockupRepository;
    private final FileStorageService fileStorageService;
    
    /**
     * Persists all mockups from a generation response to database and file system
     * 
     * @param requestId The request ID
     * @param request The original generation request
     * @param response The generation response containing mockups to persist
     * @param user The user who requested the generation
     */
    @Transactional
    public void persistGeneratedMockups(String requestId, MockupGenerationRequest request, 
                                       MockupGenerationResponse response, User user) {
        try {
            log.info("Persisting {} mockups for request {}", 
                    response.getMockups().size(), requestId);
            
            // Get all service results for metadata (Banana only for mockups)
            List<MockupGenerationResponse.ServiceResults> allServiceResults = new ArrayList<>();
            if (response.getBananaResults() != null) {
                allServiceResults.addAll(response.getBananaResults());
            }
            
            // Save individual mockups
            for (MockupGenerationResponse.GeneratedMockup mockup : response.getMockups()) {
                if (mockup.getBase64Data() != null && !mockup.getBase64Data().isEmpty()) {
                    persistSingleMockup(requestId, request, mockup, allServiceResults, user);
                }
            }
            
            log.info("Successfully persisted {} mockups for request {}", 
                    response.getMockups().size(), requestId);
            
        } catch (Exception e) {
            log.error("Error persisting mockups for request {}", requestId, e);
            throw e;
        }
    }
    
    /**
     * Persists mockups from "more mockups" generation
     * 
     * @param requestId The original request ID
     * @param newMockups The new mockups to persist
     * @param user The user who requested more mockups
     * @param description The description/theme
     * @param generationIndex The generation index (1 for original, 2+ for variations)
     */
    @Transactional
    public void persistMoreMockups(String requestId, 
                                  List<MockupGenerationResponse.GeneratedMockup> newMockups, 
                                  User user, String description, int generationIndex) {
        try {
            log.info("Persisting {} more mockups for request {}", newMockups.size(), requestId);
            
            String mockupType = (generationIndex == 1) ? "original" : "variation";
            
            for (MockupGenerationResponse.GeneratedMockup mockup : newMockups) {
                if (mockup.getBase64Data() != null && !mockup.getBase64Data().isEmpty()) {
                    persistMoreMockup(requestId, mockup, user, mockupType, description, generationIndex);
                }
            }
            
            log.info("Successfully persisted {} more mockups for request {}", 
                    newMockups.size(), requestId);
            
        } catch (Exception e) {
            log.error("Error persisting more mockups for request {}", requestId, e);
            throw e;
        }
    }
    
    /**
     * Persists a single mockup from main generation
     */
    private void persistSingleMockup(String requestId, MockupGenerationRequest request, 
                                    MockupGenerationResponse.GeneratedMockup mockup,
                                    List<MockupGenerationResponse.ServiceResults> allServiceResults, 
                                    User user) {
        
        // Find generation index from service results
        Integer generationIndex = findGenerationIndex(mockup, allServiceResults);
        
        // Determine mockup type based on generation index
        String mockupType = (generationIndex != null && generationIndex == 1) ? "original" : "variation";
        
        // Generate file name
        String fileName = fileStorageService.generateMockupFileName(mockup.getId());
        
        // Save mockup to file system
        String filePath = fileStorageService.saveMockup(
                user.getDirectoryPath(),
                requestId,
                mockupType,
                fileName,
                mockup.getBase64Data()
        );
        
        // Create database record
        GeneratedMockup generatedMockup = new GeneratedMockup();
        generatedMockup.setRequestId(requestId);
        generatedMockup.setMockupId(mockup.getId());
        generatedMockup.setUser(user);
        generatedMockup.setFileName(fileName);
        generatedMockup.setFilePath(filePath);
        generatedMockup.setDescription(mockup.getDescription());
        generatedMockup.setTheme(request.getDescription());
        generatedMockup.setGenerationIndex(generationIndex);
        generatedMockup.setMockupType(mockupType);
        
        // Calculate file size
        long fileSize = fileStorageService.getMockupFileSize(
                user.getDirectoryPath(), requestId, mockupType, fileName);
        generatedMockup.setFileSize(fileSize);
        
        generatedMockupRepository.save(generatedMockup);
    }
    
    /**
     * Persists a single mockup from "more mockups" generation
     */
    private void persistMoreMockup(String requestId, 
                                  MockupGenerationResponse.GeneratedMockup mockup, 
                                  User user, String mockupType, String description, 
                                  int generationIndex) {
        
        // Generate file name for more mockups
        String fileName = fileStorageService.generateMockupFileName(mockup.getId());
        
        // Save mockup to file system
        String filePath = fileStorageService.saveMockup(
                user.getDirectoryPath(),
                requestId,
                mockupType,
                fileName,
                mockup.getBase64Data()
        );
        
        // Create database record
        GeneratedMockup generatedMockup = new GeneratedMockup();
        generatedMockup.setRequestId(requestId);
        generatedMockup.setMockupId(mockup.getId());
        generatedMockup.setUser(user);
        generatedMockup.setFileName(fileName);
        generatedMockup.setFilePath(filePath);
        generatedMockup.setDescription(mockup.getDescription());
        generatedMockup.setTheme(description);
        generatedMockup.setGenerationIndex(generationIndex);
        generatedMockup.setMockupType(mockupType);
        
        // Calculate file size
        long fileSize = fileStorageService.getMockupFileSize(
                user.getDirectoryPath(), requestId, mockupType, fileName);
        generatedMockup.setFileSize(fileSize);
        
        generatedMockupRepository.save(generatedMockup);
    }
    
    /**
     * Finds the generation index for a mockup based on service results
     */
    private Integer findGenerationIndex(
            MockupGenerationResponse.GeneratedMockup mockup,
            List<MockupGenerationResponse.ServiceResults> allServiceResults) {
        
        // Try to find which service result contains this mockup
        for (MockupGenerationResponse.ServiceResults serviceResult : allServiceResults) {
            if (serviceResult.getMockups() != null) {
                for (MockupGenerationResponse.GeneratedMockup resultMockup : 
                        serviceResult.getMockups()) {
                    if (resultMockup.getId().equals(mockup.getId())) {
                        return serviceResult.getGenerationIndex();
                    }
                }
            }
        }
        
        // Default to generation 1 if not found
        return 1;
    }
}

