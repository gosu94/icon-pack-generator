package com.gosu.iconpackgenerator.util;

import com.gosu.iconpackgenerator.singal.SignalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ErrorMessageSanitizer {

    private static final String TEMPORARY_SERVICE_UNAVAILABLE_MESSAGE =
            "The AI service is temporarily unavailable, so your icons could not be generated. Please try again in a few minutes.";
    private final SignalMessageService signalMessageService;

    /**
     * Sanitizes error messages to provide user-friendly feedback while logging detailed errors
     */
    public String sanitizeErrorMessage(String originalMessage, String serviceName) {
        // Log the original detailed error for debugging
        log.error("Original error in {}: {}", serviceName, originalMessage);

        if (originalMessage == null) {
            return "Request failed";
        }

        String lowerMessage = originalMessage.toLowerCase();

        if (lowerMessage.contains("it may violate content policy")) {
            return "Your request could not be processed as it may violate content policy. Please try a different prompt. You have been refunded for this attempt";
        }
        // Handle specific error patterns
        if (lowerMessage.contains("422") || lowerMessage.contains("unprocessable")) {
            return "Your request could not be processed as it may violate content policy. Please try a different prompt. You have been refunded for this attempt";
        }

        if (lowerMessage.contains("401") || lowerMessage.contains("unauthorized")) {
            return "Request failed";
        }

        if (lowerMessage.contains("403") || lowerMessage.contains("forbidden")) {
            return "Request failed";
        }

        if (lowerMessage.contains("429") || lowerMessage.contains("rate limit")) {
            sendTemporaryServiceFailureSignal(serviceName, originalMessage);
            return TEMPORARY_SERVICE_UNAVAILABLE_MESSAGE;
        }

        if (lowerMessage.contains("500") || lowerMessage.contains("internal server error")) {
            return "Request failed";
        }

        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            sendTemporaryServiceFailureSignal(serviceName, originalMessage);
            return TEMPORARY_SERVICE_UNAVAILABLE_MESSAGE;
        }

        if (lowerMessage.contains("network") || lowerMessage.contains("connection")) {
            sendTemporaryServiceFailureSignal(serviceName, originalMessage);
            return TEMPORARY_SERVICE_UNAVAILABLE_MESSAGE;
        }

        // For any other technical errors, return generic message
        return "Request failed";
    }

    /**
     * Checks if an error should trigger a retry attempt
     */
    public boolean shouldRetry(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        String lowerMessage = errorMessage.toLowerCase();

        // Retry on 422 errors and network issues
        return lowerMessage.contains("422") ||
                lowerMessage.contains("unprocessable") ||
                lowerMessage.contains("timeout") ||
                lowerMessage.contains("connection");
    }

    /**
     * Checks if an error is a 422 status code specifically
     */
    public boolean is422Error(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        String lowerMessage = errorMessage.toLowerCase();
        return lowerMessage.contains("422") || lowerMessage.contains("unprocessable");
    }

    private void sendTemporaryServiceFailureSignal(String serviceName, String originalMessage) {
        String resolvedServiceName = serviceName != null && !serviceName.isBlank() ? serviceName : "unknown";
        String resolvedMessage = originalMessage != null && !originalMessage.isBlank() ? originalMessage : "no details";
        signalMessageService.sendSignalMessage(String.format(
                "[IconPackGen]: Temporary AI service failure in %s - %s",
                resolvedServiceName,
                resolvedMessage
        ));
    }
}
