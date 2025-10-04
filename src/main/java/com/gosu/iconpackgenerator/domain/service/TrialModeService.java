package com.gosu.iconpackgenerator.domain.service;

import com.gosu.iconpackgenerator.domain.dto.IconGenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service responsible for applying trial mode limitations to icon generation responses.
 * Trial users get limited number of icons per service to encourage upgrades.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrialModeService {
    
    private static final int TRIAL_ICON_LIMIT_PER_SERVICE = 5;
    
    /**
     * Applies trial mode limitations to a complete icon generation response
     * 
     * @param response The response to limit
     */
    public void applyTrialLimitations(IconGenerationResponse response) {
        log.info("Applying trial mode limitations to response");
        
        // Apply limitations to individual service results
        if (response.getFalAiResults() != null) {
            response.getFalAiResults().forEach(this::limitServiceIcons);
        }
        if (response.getRecraftResults() != null) {
            response.getRecraftResults().forEach(this::limitServiceIcons);
        }
        if (response.getPhotonResults() != null) {
            response.getPhotonResults().forEach(this::limitServiceIcons);
        }
        if (response.getGptResults() != null) {
            response.getGptResults().forEach(this::limitServiceIcons);
        }
        if (response.getBananaResults() != null) {
            response.getBananaResults().forEach(this::limitServiceIcons);
        }
        
        // Rebuild the combined icons list after limiting individual services
        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();
        addIconsFromServiceResults(allIcons, response.getFalAiResults());
        addIconsFromServiceResults(allIcons, response.getRecraftResults());
        addIconsFromServiceResults(allIcons, response.getPhotonResults());
        addIconsFromServiceResults(allIcons, response.getGptResults());
        addIconsFromServiceResults(allIcons, response.getBananaResults());
        response.setIcons(allIcons);
        
        // Add trial indicator to the overall response
        String currentMessage = response.getMessage() != null ? response.getMessage() : "Generated";
        response.setMessage(currentMessage + " - Trial Mode: Limited to " + TRIAL_ICON_LIMIT_PER_SERVICE + " icons per service");
        
        log.info("Applied trial limitations to response. Final icon count: {}", allIcons.size());
    }
    
    /**
     * Limits the icons in a single ServiceResults to the trial limit
     * 
     * @param serviceResults The service results to limit
     */
    private void limitServiceIcons(IconGenerationResponse.ServiceResults serviceResults) {
        if (serviceResults == null || serviceResults.getIcons() == null || 
            serviceResults.getIcons().size() <= TRIAL_ICON_LIMIT_PER_SERVICE) {
            return; // No need to limit if at or below limit
        }
        
        List<IconGenerationResponse.GeneratedIcon> originalIcons = new ArrayList<>(serviceResults.getIcons());
        Collections.shuffle(originalIcons); // Randomize the selection
        
        List<IconGenerationResponse.GeneratedIcon> limitedIcons = originalIcons.subList(0, TRIAL_ICON_LIMIT_PER_SERVICE);
        serviceResults.setIcons(limitedIcons);
        
        // Update the message to indicate limitation
        String currentMessage = serviceResults.getMessage() != null ? serviceResults.getMessage() : "";
        serviceResults.setMessage(currentMessage + " (Trial: " + TRIAL_ICON_LIMIT_PER_SERVICE + " of " + originalIcons.size() + " icons)");
        
        log.info("Limited icons to {} random ones for trial user in service: {}", 
                TRIAL_ICON_LIMIT_PER_SERVICE, serviceResults.getServiceName());
    }
    
    /**
     * Adds icons from service results to the combined list
     */
    private void addIconsFromServiceResults(List<IconGenerationResponse.GeneratedIcon> allIcons, 
                                          List<IconGenerationResponse.ServiceResults> serviceResults) {
        if (serviceResults != null) {
            for (IconGenerationResponse.ServiceResults result : serviceResults) {
                if (result.getIcons() != null) {
                    allIcons.addAll(result.getIcons());
                }
            }
        }
    }
    
    /**
     * Gets the trial icon limit per service
     * 
     * @return The number of icons trial users get per service
     */
    public int getTrialIconLimitPerService() {
        return TRIAL_ICON_LIMIT_PER_SERVICE;
    }
}
