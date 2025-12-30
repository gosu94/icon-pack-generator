package com.gosu.iconpackgenerator.domain.illustrations.service;

import com.gosu.iconpackgenerator.util.FileStorageService;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
import com.gosu.iconpackgenerator.domain.illustrations.entity.GeneratedIllustration;
import com.gosu.iconpackgenerator.domain.illustrations.repository.GeneratedIllustrationRepository;
import com.gosu.iconpackgenerator.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for persisting generated illustrations to database and file system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IllustrationPersistenceService {
    
    private final GeneratedIllustrationRepository generatedIllustrationRepository;
    private final FileStorageService fileStorageService;
    
    /**
     * Persists all illustrations from a generation response to database and file system
     * 
     * @param requestId The request ID
     * @param request The original generation request
     * @param response The generation response containing illustrations to persist
     * @param user The user who requested the generation
     * @param isWatermarked Whether these illustrations are watermarked
     */
    @Transactional
    public void persistGeneratedIllustrations(String requestId, IllustrationGenerationRequest request, 
                                            IllustrationGenerationResponse response, User user,
                                            boolean isWatermarked,
                                            boolean storePrivately) {
        try {
            log.info("Persisting {} illustrations for request {}", 
                    response.getIllustrations().size(), requestId);
            
            // Get all service results for metadata (Banana only for illustrations)
            List<IllustrationGenerationResponse.ServiceResults> allServiceResults = new ArrayList<>();
            if (response.getBananaResults() != null) {
                allServiceResults.addAll(response.getBananaResults());
            }
            
            // Save individual illustrations
            for (IllustrationGenerationResponse.GeneratedIllustration illustration : response.getIllustrations()) {
                if (illustration.getBase64Data() != null && !illustration.getBase64Data().isEmpty()) {
                    persistSingleIllustration(requestId, request, illustration, allServiceResults, user, isWatermarked, storePrivately);
                }
            }
            
            log.info("Successfully persisted {} illustrations for request {}", 
                    response.getIllustrations().size(), requestId);
            
        } catch (Exception e) {
            log.error("Error persisting illustrations for request {}", requestId, e);
            throw e;
        }
    }

    @Transactional
    public void persistGeneratedIllustrations(String requestId, IllustrationGenerationRequest request,
                                              IllustrationGenerationResponse response, User user) {
        persistGeneratedIllustrations(requestId, request, response, user, false, false);
    }
    
    /**
     * Persists illustrations from "more illustrations" generation
     * 
     * @param requestId The original request ID
     * @param newIllustrations The new illustrations to persist
     * @param user The user who requested more illustrations
     * @param generalDescription The general description/theme
     * @param generationIndex The generation index (1 for original, 2+ for variations)
     */
    @Transactional
    public void persistMoreIllustrations(String requestId, 
                                        List<IllustrationGenerationResponse.GeneratedIllustration> newIllustrations, 
                                        User user, String generalDescription, int generationIndex,
                                        boolean isWatermarked,
                                        boolean storePrivately) {
        try {
            log.info("Persisting {} more illustrations for request {}", newIllustrations.size(), requestId);
            
            String illustrationType = (generationIndex == 1) ? "original" : "variation";
            
            for (IllustrationGenerationResponse.GeneratedIllustration illustration : newIllustrations) {
                if (illustration.getBase64Data() != null && !illustration.getBase64Data().isEmpty()) {
                    persistMoreIllustration(requestId, illustration, user, illustrationType, 
                                          generalDescription, generationIndex, isWatermarked, storePrivately);
                }
            }
            
            log.info("Successfully persisted {} more illustrations for request {}", 
                    newIllustrations.size(), requestId);
            
        } catch (Exception e) {
            log.error("Error persisting more illustrations for request {}", requestId, e);
            throw e;
        }
    }

    @Transactional
    public void persistMoreIllustrations(String requestId, 
                                        List<IllustrationGenerationResponse.GeneratedIllustration> newIllustrations, 
                                        User user, String generalDescription, int generationIndex) {
        persistMoreIllustrations(requestId, newIllustrations, user, generalDescription, generationIndex, false, false);
    }
    
    /**
     * Persists a single illustration from main generation
     */
    private void persistSingleIllustration(String requestId, IllustrationGenerationRequest request, 
                                          IllustrationGenerationResponse.GeneratedIllustration illustration,
                                          List<IllustrationGenerationResponse.ServiceResults> allServiceResults, 
                                          User user,
                                          boolean isWatermarked,
                                          boolean storePrivately) {
        
        // Find generation index from service results
        Integer generationIndex = findGenerationIndex(illustration, allServiceResults);
        
        // Determine illustration type based on generation index
        String illustrationType = (generationIndex != null && generationIndex == 1) ? "original" : "variation";
        
        // Generate file name
        String fileName = fileStorageService.generateIllustrationFileName(
                illustration.getId(), 
                illustration.getGridPosition()
        );
        
        String storageType = isWatermarked ? illustrationType + "-trial" : illustrationType;

        String filePath = storePrivately
                ? fileStorageService.saveIllustrationPrivate(
                        user.getDirectoryPath(),
                        requestId,
                        storageType,
                        fileName,
                        illustration.getBase64Data())
                : fileStorageService.saveIllustration(
                        user.getDirectoryPath(),
                        requestId,
                        storageType,
                        fileName,
                        illustration.getBase64Data());
        
        // Create database record
        GeneratedIllustration generatedIllustration = new GeneratedIllustration();
        generatedIllustration.setRequestId(requestId);
        generatedIllustration.setIllustrationId(illustration.getId());
        generatedIllustration.setUser(user);
        generatedIllustration.setFileName(fileName);
        generatedIllustration.setFilePath(filePath);
        generatedIllustration.setGridPosition(illustration.getGridPosition());
        generatedIllustration.setDescription(illustration.getDescription());
        generatedIllustration.setTheme(request.getGeneralDescription());
        generatedIllustration.setIllustrationCount(request.getIllustrationCount());
        generatedIllustration.setGenerationIndex(generationIndex);
        generatedIllustration.setIllustrationType(illustrationType);
        generatedIllustration.setIsWatermarked(isWatermarked);
        
        // Calculate file size
        long fileSize = storePrivately
                ? fileStorageService.getPrivateIllustrationFileSize(
                        user.getDirectoryPath(), requestId, storageType, fileName)
                : fileStorageService.getIllustrationFileSize(
                        user.getDirectoryPath(), requestId, storageType, fileName);
        generatedIllustration.setFileSize(fileSize);
        
        generatedIllustrationRepository.save(generatedIllustration);
    }
    
    /**
     * Persists a single illustration from "more illustrations" generation
     */
    private void persistMoreIllustration(String requestId, 
                                        IllustrationGenerationResponse.GeneratedIllustration illustration, 
                                        User user, String illustrationType, String generalDescription, 
                                        int generationIndex,
                                        boolean isWatermarked,
                                        boolean storePrivately) {
        
        // Generate file name for more illustrations
        String fileName = String.format("illustration_%s_%d.png",
                illustration.getId().substring(0, 8),
                illustration.getGridPosition());
        
        String storageType = isWatermarked ? illustrationType + "-trial" : illustrationType;

        String filePath = storePrivately
                ? fileStorageService.saveIllustrationPrivate(
                        user.getDirectoryPath(),
                        requestId,
                        storageType,
                        fileName,
                        illustration.getBase64Data())
                : fileStorageService.saveIllustration(
                        user.getDirectoryPath(),
                        requestId,
                        storageType,
                        fileName,
                        illustration.getBase64Data());
        
        // Create database record
        GeneratedIllustration generatedIllustration = new GeneratedIllustration();
        generatedIllustration.setRequestId(requestId);
        generatedIllustration.setIllustrationId(illustration.getId());
        generatedIllustration.setUser(user);
        generatedIllustration.setFileName(fileName);
        generatedIllustration.setFilePath(filePath);
        generatedIllustration.setGridPosition(illustration.getGridPosition());
        generatedIllustration.setDescription(illustration.getDescription());
        generatedIllustration.setTheme(generalDescription);
        generatedIllustration.setIllustrationCount(4); // Always 4 for 2x2 grid
        generatedIllustration.setGenerationIndex(generationIndex);
        generatedIllustration.setIllustrationType(illustrationType);
        generatedIllustration.setIsWatermarked(isWatermarked);
        
        // Calculate file size
        long fileSize = storePrivately
                ? fileStorageService.getPrivateIllustrationFileSize(
                        user.getDirectoryPath(), requestId, storageType, fileName)
                : fileStorageService.getIllustrationFileSize(
                        user.getDirectoryPath(), requestId, storageType, fileName);
        generatedIllustration.setFileSize(fileSize);
        
        generatedIllustrationRepository.save(generatedIllustration);
    }
    
    /**
     * Finds the generation index for an illustration based on service results
     */
    private Integer findGenerationIndex(
            IllustrationGenerationResponse.GeneratedIllustration illustration,
            List<IllustrationGenerationResponse.ServiceResults> allServiceResults) {
        
        // Try to find which service result contains this illustration
        for (IllustrationGenerationResponse.ServiceResults serviceResult : allServiceResults) {
            if (serviceResult.getIllustrations() != null) {
                for (IllustrationGenerationResponse.GeneratedIllustration resultIllustration : 
                        serviceResult.getIllustrations()) {
                    if (resultIllustration.getId().equals(illustration.getId())) {
                        return serviceResult.getGenerationIndex();
                    }
                }
            }
        }
        
        // Default to generation 1 if not found
        return 1;
    }
}
