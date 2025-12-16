package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.util.ErrorMessageSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for detecting service failures, determining if they warrant refunds,
 * and handling the refund process for failed icon generation requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceFailureHandler {
    
    private final CoinManagementService coinManagementService;
    private final ErrorMessageSanitizer errorMessageSanitizer;
    
    /**
     * Result of failure analysis
     */
    public static class FailureAnalysisResult {
        private final boolean shouldRefund;
        private final String refundMessage;
        private final String originalMessage;
        
        public FailureAnalysisResult(boolean shouldRefund, String refundMessage, String originalMessage) {
            this.shouldRefund = shouldRefund;
            this.refundMessage = refundMessage;
            this.originalMessage = originalMessage;
        }
        
        public boolean shouldRefund() { return shouldRefund; }
        public String getRefundMessage() { return refundMessage; }
        public String getOriginalMessage() { return originalMessage; }
        
        public static FailureAnalysisResult refundRequired(String refundMessage, String originalMessage) {
            return new FailureAnalysisResult(true, refundMessage, originalMessage);
        }
        
        public static FailureAnalysisResult noRefundRequired(String originalMessage) {
            return new FailureAnalysisResult(false, null, originalMessage);
        }
    }
    
    /**
     * Analyzes service failures to determine if coins should be refunded
     * 
     * @param falAiResults Results from Flux AI service
     * @param recraftResults Results from Recraft service
     * @param photonResults Results from Photon service
     * @param gptResults Results from GPT service
     * @param gpt15Results Results from GPT-1.5 service
     * @param bananaResults Results from Banana service
     * @return FailureAnalysisResult indicating whether refund is needed
     */
    public FailureAnalysisResult analyzeServiceFailures(
            List<IconGenerationResponse.ServiceResults> falAiResults,
            List<IconGenerationResponse.ServiceResults> recraftResults,
            List<IconGenerationResponse.ServiceResults> photonResults,
            List<IconGenerationResponse.ServiceResults> gptResults,
            List<IconGenerationResponse.ServiceResults> gpt15Results,
            List<IconGenerationResponse.ServiceResults> bananaResults) {
        
        List<List<IconGenerationResponse.ServiceResults>> allServiceResults = List.of(
                falAiResults, recraftResults, photonResults, gptResults, gpt15Results, bananaResults
        );
        
        boolean hasEnabledService = false;
        boolean allEnabledServicesFailed = true;
        
        for (List<IconGenerationResponse.ServiceResults> serviceResults : allServiceResults) {
            for (IconGenerationResponse.ServiceResults result : serviceResults) {
                if (!"disabled".equals(result.getStatus())) {
                    hasEnabledService = true;
                    
                    if ("success".equals(result.getStatus())) {
                        // At least one service succeeded, don't refund
                        allEnabledServicesFailed = false;
                        break;
                    }
                    
                }
            }
            if (!allEnabledServicesFailed) {
                break;
            }
        }
        
        boolean shouldRefund = hasEnabledService && allEnabledServicesFailed;
        log.info("Refund decision: hasEnabledService={}, allEnabledServicesFailed={}, shouldRefund={}", 
                hasEnabledService, allEnabledServicesFailed, shouldRefund);
        
        if (shouldRefund) {
            return FailureAnalysisResult.refundRequired(
                "All AI services are temporarily unavailable. Your coins have been refunded. Please try again in a few minutes.",
                "All enabled services failed to generate icons"
            );
        } else {
            return FailureAnalysisResult.noRefundRequired("All enabled services failed to generate icons");
        }
    }
    
    /**
     * Analyzes single service failure for "more icons" generation
     * 
     * @param errorMessage The error message from the service
     * @param serviceName The name of the service that failed
     * @return FailureAnalysisResult indicating whether refund is needed
     */
    public FailureAnalysisResult analyzeSingleServiceFailure(String errorMessage, String serviceName) {
        if (isTemporaryServiceFailure(errorMessage)) {
            String refundMessage = "The " + serviceName + " service is temporarily unavailable. Your coins have been refunded. Please try again in a few minutes.";
            return FailureAnalysisResult.refundRequired(refundMessage, errorMessage);
        } else {
            return FailureAnalysisResult.noRefundRequired("Failed to generate more icons: " + errorMessage);
        }
    }
    
    /**
     * Processes a refund for failed icon generation
     * 
     * @param user The user to refund coins to
     * @param deductedAmount The amount of coins that were deducted
     * @param usedTrialCoins Whether trial coins were used
     * @param requestId The request ID for logging purposes
     */
    public void processRefund(User user, int deductedAmount, boolean usedTrialCoins, String requestId) {
        try {
            coinManagementService.refundCoins(user, deductedAmount, usedTrialCoins);
            log.info("Refunded {} {} coin(s) to user {} due to service temporary unavailability. Request ID: {}", 
                    deductedAmount, usedTrialCoins ? "trial" : "regular", user.getEmail(), requestId);
        } catch (Exception e) {
            log.error("Failed to refund coins to user {} for request {}", user.getEmail(), requestId, e);
            throw e;
        }
    }
    
    /**
     * Determines if a service failure is temporary and warrants a refund.
     * This includes 422 errors, timeout errors, and other service unavailability patterns.
     */
    private boolean isTemporaryServiceFailure(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        
        String lowerMessage = errorMessage.toLowerCase();
        
        // Check for patterns that indicate temporary unavailability
        return lowerMessage.contains("temporarily unavailable") ||
               lowerMessage.contains("service unavailable") ||
               lowerMessage.contains("timeout") ||
               lowerMessage.contains("connection") ||
               lowerMessage.contains("network") ||
               lowerMessage.contains("server error") ||
               lowerMessage.contains("internal error") ||
               lowerMessage.contains("429") || // Rate limiting
               lowerMessage.contains("502") || // Bad Gateway
               lowerMessage.contains("503") || // Service Unavailable
               lowerMessage.contains("504") || // Gateway Timeout
               errorMessageSanitizer.is422Error(errorMessage); // Unprocessable Entity (often temporary)
    }
}
