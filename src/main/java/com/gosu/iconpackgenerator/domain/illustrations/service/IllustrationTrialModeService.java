package com.gosu.iconpackgenerator.domain.illustrations.service;

import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
import com.gosu.iconpackgenerator.util.WatermarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for applying trial mode watermarks to illustration generation responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IllustrationTrialModeService {

    private static final String TRIAL_MESSAGE_SUFFIX = " - Trial Mode: Watermark applied";
    private final WatermarkService watermarkService;
    
    /**
     * Applies trial mode watermark to a complete illustration generation response.
     *
     * @param response The response to watermark
     */
    public void applyTrialWatermark(IllustrationGenerationResponse response) {
        log.info("Applying trial watermark to illustration response");
        
        // Apply limitations to Banana service results (only service for illustrations)
        if (response.getBananaResults() != null) {
            response.getBananaResults().forEach(this::watermarkServiceIllustrations);
        }
        
        // Rebuild the combined illustrations list after watermarking
        List<IllustrationGenerationResponse.GeneratedIllustration> allIllustrations = new ArrayList<>();
        addIllustrationsFromServiceResults(allIllustrations, response.getBananaResults());
        response.setIllustrations(allIllustrations);
        
        // Add trial indicator to the overall response
        String currentMessage = response.getMessage() != null ? response.getMessage() : "Generated";
        response.setMessage(currentMessage + TRIAL_MESSAGE_SUFFIX);
    }
    
    /**
     * Applies watermark to the illustrations in a single ServiceResults.
     */
    private void watermarkServiceIllustrations(IllustrationGenerationResponse.ServiceResults serviceResults) {
        if (serviceResults == null || serviceResults.getIllustrations() == null) {
            return;
        }

        for (IllustrationGenerationResponse.GeneratedIllustration illustration : serviceResults.getIllustrations()) {
            if (illustration.getBase64Data() != null && !illustration.getBase64Data().isBlank()) {
                illustration.setBase64Data(watermarkService.applyTrialWatermark(illustration.getBase64Data()));
            }
        }
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
    
}
