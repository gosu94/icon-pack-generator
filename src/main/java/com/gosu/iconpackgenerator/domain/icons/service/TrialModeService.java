package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Applies trial mode limitations to a complete icon generation response and
     * returns metadata describing what was limited for downstream consumers.
     *
     * @param response The response to limit
     * @return Details of the applied trial limitation (visible icon IDs, limited services)
     */
    public TrialLimitationResult applyTrialLimitations(IconGenerationResponse response) {
        log.info("Applying trial mode limitations to response");
        TrialLimitationResult limitationResult = new TrialLimitationResult();
        
        // Apply limitations to individual service results
        if (response.getFalAiResults() != null) {
            response.getFalAiResults().forEach(result -> limitServiceIcons(response, result, limitationResult));
        }
        if (response.getRecraftResults() != null) {
            response.getRecraftResults().forEach(result -> limitServiceIcons(response, result, limitationResult));
        }
        if (response.getPhotonResults() != null) {
            response.getPhotonResults().forEach(result -> limitServiceIcons(response, result, limitationResult));
        }
        if (response.getGptResults() != null) {
            response.getGptResults().forEach(result -> limitServiceIcons(response, result, limitationResult));
        }
        if (response.getGpt15Results() != null) {
            response.getGpt15Results().forEach(result -> limitServiceIcons(response, result, limitationResult));
        }
        if (response.getBananaResults() != null) {
            response.getBananaResults().forEach(result -> limitServiceIcons(response, result, limitationResult));
        }
        
        // Rebuild the combined icons list after limiting individual services
        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();
        addIconsFromServiceResults(allIcons, response.getFalAiResults());
        addIconsFromServiceResults(allIcons, response.getRecraftResults());
        addIconsFromServiceResults(allIcons, response.getPhotonResults());
        addIconsFromServiceResults(allIcons, response.getGptResults());
        addIconsFromServiceResults(allIcons, response.getGpt15Results());
        addIconsFromServiceResults(allIcons, response.getBananaResults());
        response.setIcons(allIcons);
        
        // Add trial indicator to the overall response
        String currentMessage = response.getMessage() != null ? response.getMessage() : "Generated";
        response.setMessage(currentMessage + " - Trial Mode: Limited to " + TRIAL_ICON_LIMIT_PER_SERVICE + " icons per service");
        
        log.info("Applied trial limitations to response. Final icon count: {}", allIcons.size());
        return limitationResult;
    }
    
    /**
     * Limits the icons in a single ServiceResults to the trial limit
     * 
     * @param response       The response the service belongs to
     * @param serviceResults The service results to limit
     * @param limitationResult Collector for limitation metadata
     */
    private void limitServiceIcons(IconGenerationResponse response,
                                   IconGenerationResponse.ServiceResults serviceResults,
                                   TrialLimitationResult limitationResult) {
        if (serviceResults == null || serviceResults.getIcons() == null ||
                serviceResults.getIcons().size() <= TRIAL_ICON_LIMIT_PER_SERVICE) {
            return; // No need to limit if at or below limit
        }
        limitationResult.addLimitedService(serviceResults.getServiceName());
        
        List<IconGenerationResponse.GeneratedIcon> originalIcons = serviceResults.getIcons();
        List<IconGenerationResponse.GeneratedIcon> limitedIcons =
                new ArrayList<>(originalIcons.subList(0, TRIAL_ICON_LIMIT_PER_SERVICE));
        serviceResults.setIcons(limitedIcons);
        limitedIcons.forEach(icon -> {
            if (icon.getId() != null) {
                limitationResult.addAllowedIconId(icon.getId());
            }
        });
        
        // Update the message to indicate limitation
        String currentMessage = serviceResults.getMessage() != null ? serviceResults.getMessage() : "";
        serviceResults.setMessage(currentMessage + " (Trial: " + TRIAL_ICON_LIMIT_PER_SERVICE + " of " + originalIcons.size() + " icons)");
        
        log.info("Limited icons to the first {} for trial user in service: {}",
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

    public static class TrialLimitationResult {
        private final Set<String> allowedIconIds = new HashSet<>();
        private final Set<String> limitedServices = new HashSet<>();

        public void addAllowedIconId(String iconId) {
            if (iconId != null) {
                allowedIconIds.add(iconId);
            }
        }

        public void addLimitedService(String serviceName) {
            if (serviceName != null) {
                limitedServices.add(serviceName);
            }
        }

        public boolean isServiceLimited(String serviceName) {
            return serviceName != null && limitedServices.contains(serviceName);
        }

        public boolean isIconAllowed(String iconId) {
            return iconId != null && allowedIconIds.contains(iconId);
        }

        public boolean hasAllowedIconIds() {
            return !allowedIconIds.isEmpty();
        }
    }

}
