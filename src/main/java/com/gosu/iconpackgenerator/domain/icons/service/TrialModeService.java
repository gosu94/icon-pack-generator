package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.util.WatermarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for applying trial mode watermarks to icon generation responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrialModeService {

    private static final String TRIAL_MESSAGE_SUFFIX = " - Trial Mode: Watermark applied";
    private final WatermarkService watermarkService;
    
    /**
     * Applies a trial watermark to a complete icon generation response.
     *
     * @param response The response to limit
     */
    public void applyTrialWatermark(IconGenerationResponse response) {
        log.info("Applying trial watermark to response");

        if (response.getFalAiResults() != null) {
            response.getFalAiResults().forEach(this::watermarkServiceIcons);
        }
        if (response.getRecraftResults() != null) {
            response.getRecraftResults().forEach(this::watermarkServiceIcons);
        }
        if (response.getPhotonResults() != null) {
            response.getPhotonResults().forEach(this::watermarkServiceIcons);
        }
        if (response.getGptResults() != null) {
            response.getGptResults().forEach(this::watermarkServiceIcons);
        }
        if (response.getGpt15Results() != null) {
            response.getGpt15Results().forEach(this::watermarkServiceIcons);
        }
        if (response.getBananaResults() != null) {
            response.getBananaResults().forEach(this::watermarkServiceIcons);
        }

        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();
        addIconsFromServiceResults(allIcons, response.getFalAiResults());
        addIconsFromServiceResults(allIcons, response.getRecraftResults());
        addIconsFromServiceResults(allIcons, response.getPhotonResults());
        addIconsFromServiceResults(allIcons, response.getGptResults());
        addIconsFromServiceResults(allIcons, response.getGpt15Results());
        addIconsFromServiceResults(allIcons, response.getBananaResults());
        response.setIcons(allIcons);

        String currentMessage = response.getMessage() != null ? response.getMessage() : "Generated";
        response.setMessage(currentMessage + TRIAL_MESSAGE_SUFFIX);
    }
    
    /**
     * Applies watermark to the icons in a single ServiceResults.
     */
    private void watermarkServiceIcons(IconGenerationResponse.ServiceResults serviceResults) {
        if (serviceResults == null || serviceResults.getIcons() == null) {
            return;
        }

        for (IconGenerationResponse.GeneratedIcon icon : serviceResults.getIcons()) {
            if (icon.getBase64Data() != null && !icon.getBase64Data().isBlank()) {
                icon.setBase64Data(watermarkService.applyTrialWatermark(icon.getBase64Data()));
            }
        }
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
    
}
