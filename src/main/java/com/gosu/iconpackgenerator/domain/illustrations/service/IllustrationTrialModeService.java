package com.gosu.iconpackgenerator.domain.illustrations.service;

import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service responsible for applying trial mode limitations to illustration generation responses.
 * Trial users get limited number of illustrations (2 out of 4) to encourage upgrades.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IllustrationTrialModeService {
    
    private static final int TRIAL_ILLUSTRATION_LIMIT = 2;
    
    /**
     * Applies trial mode limitations to a complete illustration generation response
     * 
     * @param response The response to limit
     */
    public void applyTrialLimitations(IllustrationGenerationResponse response) {
        log.info("Applying trial mode limitations to illustration response");
        
        // Apply limitations to Banana service results (only service for illustrations)
        if (response.getBananaResults() != null) {
            response.getBananaResults().forEach(this::limitServiceIllustrations);
        }
        
        // Rebuild the combined illustrations list after limiting individual services
        List<IllustrationGenerationResponse.GeneratedIllustration> allIllustrations = new ArrayList<>();
        addIllustrationsFromServiceResults(allIllustrations, response.getBananaResults());
        response.setIllustrations(allIllustrations);
        
        // Add trial indicator to the overall response
        String currentMessage = response.getMessage() != null ? response.getMessage() : "Generated";
        response.setMessage(currentMessage + " - Trial Mode: Limited to " + TRIAL_ILLUSTRATION_LIMIT + " random illustrations");
        
        log.info("Applied trial limitations to illustration response. Final illustration count: {}", allIllustrations.size());
    }
    
    /**
     * Limits the illustrations in a single ServiceResults to the trial limit
     * 
     * @param serviceResults The service results to limit
     */
    private void limitServiceIllustrations(IllustrationGenerationResponse.ServiceResults serviceResults) {
        if (serviceResults == null || serviceResults.getIllustrations() == null || 
            serviceResults.getIllustrations().size() <= TRIAL_ILLUSTRATION_LIMIT) {
            return; // No need to limit if at or below limit
        }
        
        List<IllustrationGenerationResponse.GeneratedIllustration> originalIllustrations = 
                new ArrayList<>(serviceResults.getIllustrations());
        Collections.shuffle(originalIllustrations); // Randomize the selection
        
        List<IllustrationGenerationResponse.GeneratedIllustration> limitedIllustrations = 
                originalIllustrations.subList(0, TRIAL_ILLUSTRATION_LIMIT);
        serviceResults.setIllustrations(limitedIllustrations);
        
        // Update the message to indicate limitation
        String currentMessage = serviceResults.getMessage() != null ? serviceResults.getMessage() : "";
        serviceResults.setMessage(currentMessage + " (Trial: " + TRIAL_ILLUSTRATION_LIMIT + " of " + 
                originalIllustrations.size() + " illustrations)");
        
        log.info("Limited illustrations to {} random ones for trial user in service: {}", 
                TRIAL_ILLUSTRATION_LIMIT, serviceResults.getServiceName());
    }
    
    /**
     * Adds illustrations from service results to the combined list
     */
    private void addIllustrationsFromServiceResults(
            List<IllustrationGenerationResponse.GeneratedIllustration> allIllustrations, 
            List<IllustrationGenerationResponse.ServiceResults> serviceResults) {
        if (serviceResults != null) {
            for (IllustrationGenerationResponse.ServiceResults result : serviceResults) {
                if (result.getIllustrations() != null) {
                    allIllustrations.addAll(result.getIllustrations());
                }
            }
        }
    }
    
    /**
     * Gets the trial illustration limit
     * 
     * @return The number of illustrations trial users get
     */
    public int getTrialIllustrationLimit() {
        return TRIAL_ILLUSTRATION_LIMIT;
    }
}

