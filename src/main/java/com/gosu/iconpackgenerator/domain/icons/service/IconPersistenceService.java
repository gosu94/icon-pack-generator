package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for persisting generated icons to database and file system.
 * Handles both main generation and "more icons" generation persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IconPersistenceService {
    
    private final GeneratedIconRepository generatedIconRepository;
    private final FileStorageService fileStorageService;
    
    @Transactional
    public void persistGeneratedIcons(String requestId, IconGenerationRequest request,
                                      IconGenerationResponse response, User user) {
        persistGeneratedIcons(requestId, request, response, user, null);
    }

    /**
     * Persists all icons from an icon generation response to database and file system
     *
     * @param requestId       The request ID
     * @param request         The original generation request
     * @param response        The generation response containing icons to persist
     * @param user            The user who requested the generation
     * @param trialLimitResult Optional limitation metadata describing which icons should be persisted
     */
    @Transactional
    public void persistGeneratedIcons(String requestId, IconGenerationRequest request,
                                      IconGenerationResponse response, User user,
                                      TrialModeService.TrialLimitationResult trialLimitResult) {
        try {
            log.info("Persisting {} icons for request {}", response.getIcons().size(), requestId);
            
            // Get all service results for metadata
            List<IconGenerationResponse.ServiceResults> allServiceResults = new ArrayList<>();
            allServiceResults.addAll(response.getFalAiResults());
            allServiceResults.addAll(response.getRecraftResults());
            allServiceResults.addAll(response.getPhotonResults());
            allServiceResults.addAll(response.getGptResults());
            allServiceResults.addAll(response.getBananaResults());
            
            int persistedCount = 0;
            for (IconGenerationResponse.GeneratedIcon icon : response.getIcons()) {
                if (!shouldPersistIcon(icon, trialLimitResult)) {
                    continue;
                }

                if (icon.getBase64Data() != null && !icon.getBase64Data().isEmpty()) {
                    persistSingleIcon(requestId, request, icon, allServiceResults, user);
                    persistedCount++;
                }
            }
            
            log.info("Successfully persisted {} icons for request {}", persistedCount, requestId);
            
        } catch (Exception e) {
            log.error("Error persisting icons for request {}", requestId, e);
            throw e;
        }
    }
    
    /**
     * Persists icons from "more icons" generation
     * 
     * @param requestId The original request ID
     * @param newIcons The new icons to persist
     * @param user The user who requested more icons
     * @param serviceName The service that generated the icons
     * @param generalDescription The general description/theme
     * @param generationIndex The generation index (1 for original, 2+ for variations)
     */
    @Transactional
    public void persistMoreIcons(String requestId, List<IconGenerationResponse.GeneratedIcon> newIcons, 
                               User user, String serviceName, String generalDescription, int generationIndex) {
        try {
            log.info("Persisting {} more icons for request {}", newIcons.size(), requestId);
            
            String iconType = (generationIndex == 1) ? "original" : "variation";
            
            for (IconGenerationResponse.GeneratedIcon icon : newIcons) {
                if (icon.getBase64Data() != null && !icon.getBase64Data().isEmpty()) {
                    persistMoreIcon(requestId, icon, user, iconType, generalDescription, generationIndex);
                }
            }
            
            log.info("Successfully persisted {} more icons for request {}", newIcons.size(), requestId);
            
        } catch (Exception e) {
            log.error("Error persisting more icons for request {}", requestId, e);
            throw e;
        }
    }
    
    /**
     * Persists a single icon from main generation
     */
    private void persistSingleIcon(String requestId, IconGenerationRequest request, 
                                 IconGenerationResponse.GeneratedIcon icon,
                                 List<IconGenerationResponse.ServiceResults> allServiceResults, User user) {
        
        // Find generation index from service results
        Integer generationIndex = findGenerationIndex(icon, allServiceResults);
        
        // Determine icon type based on generation index
        String iconType = (generationIndex != null && generationIndex == 1) ? "original" : "variation";
        
        // Generate file name
        String fileName = fileStorageService.generateIconFileName(
                icon.getId(), 
                icon.getGridPosition()
        );
        
        // Save icon to file system
        String filePath = fileStorageService.saveIcon(
                user.getDirectoryPath(),
                requestId,
                iconType,
                fileName,
                icon.getBase64Data()
        );
        
        // Create database record
        GeneratedIcon generatedIcon = new GeneratedIcon();
        generatedIcon.setRequestId(requestId);
        generatedIcon.setIconId(icon.getId());
        generatedIcon.setUser(user);
        generatedIcon.setFileName(fileName);
        generatedIcon.setFilePath(filePath);
        generatedIcon.setServiceSource(icon.getServiceSource());
        generatedIcon.setGridPosition(icon.getGridPosition());
        generatedIcon.setDescription(icon.getDescription());
        generatedIcon.setTheme(request.getGeneralDescription());
        generatedIcon.setIconCount(request.getIconCount());
        generatedIcon.setGenerationIndex(generationIndex);
        generatedIcon.setIconType(iconType);
        generatedIcon.setUsedPromptEnhancer(request.isEnhancePrompt());
        
        // Calculate file size
        long fileSize = fileStorageService.getFileSize(user.getDirectoryPath(), requestId, iconType, fileName);
        generatedIcon.setFileSize(fileSize);
        
        generatedIconRepository.save(generatedIcon);
    }
    
    /**
     * Persists a single icon from "more icons" generation
     */
    private void persistMoreIcon(String requestId, IconGenerationResponse.GeneratedIcon icon, 
                               User user, String iconType, String generalDescription, int generationIndex) {
        
        // Generate file name for more icons
        String fileName = String.format("icon_%s_%d.png",
                icon.getId().substring(0, 8),
                icon.getGridPosition());
        
        // Save icon to file system
        String filePath = fileStorageService.saveIcon(
                user.getDirectoryPath(),
                requestId,
                iconType,
                fileName,
                icon.getBase64Data()
        );
        
        // Create database record
        GeneratedIcon generatedIcon = new GeneratedIcon();
        generatedIcon.setRequestId(requestId);
        generatedIcon.setIconId(icon.getId());
        generatedIcon.setUser(user);
        generatedIcon.setFileName(fileName);
        generatedIcon.setFilePath(filePath);
        generatedIcon.setServiceSource(icon.getServiceSource());
        generatedIcon.setGridPosition(icon.getGridPosition());
        generatedIcon.setDescription(icon.getDescription());
        generatedIcon.setTheme(generalDescription);
        generatedIcon.setIconCount(9); // More icons always generates 9 icons
        generatedIcon.setGenerationIndex(generationIndex);
        generatedIcon.setIconType(iconType);
        generatedIcon.setUsedPromptEnhancer(false);
        
        // Calculate file size
        long fileSize = fileStorageService.getFileSize(user.getDirectoryPath(), requestId, iconType, fileName);
        generatedIcon.setFileSize(fileSize);
        
        generatedIconRepository.save(generatedIcon);
    }
    
    /**
     * Finds the generation index for an icon based on service results
     */
    private Integer findGenerationIndex(IconGenerationResponse.GeneratedIcon icon, 
                                      List<IconGenerationResponse.ServiceResults> allServiceResults) {
        return allServiceResults.stream()
                .filter(result -> icon.getServiceSource().equals(result.getServiceName()))
                .filter(result -> result.getIcons() != null && result.getIcons().contains(icon))
                .map(IconGenerationResponse.ServiceResults::getGenerationIndex)
                .findFirst()
                .orElse(1);
    }

    private boolean shouldPersistIcon(IconGenerationResponse.GeneratedIcon icon, TrialModeService.TrialLimitationResult trialLimitResult) {
        if (icon == null) {
            return true;
        }

        if (trialLimitResult == null || !trialLimitResult.hasAllowedIconIds()) {
            return true;
        }

        String serviceSource = icon.getServiceSource();
        if (trialLimitResult.isServiceLimited(serviceSource)) {
            return trialLimitResult.isIconAllowed(icon.getId());
        }

        return true;
    }
}
