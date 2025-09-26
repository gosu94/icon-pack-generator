package com.gosu.iconpackgenerator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ErrorMessageSanitizer {

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
            return "Request failed";
        }

        if (lowerMessage.contains("401") || lowerMessage.contains("unauthorized")) {
            return "Request failed";
        }

        if (lowerMessage.contains("403") || lowerMessage.contains("forbidden")) {
            return "Request failed";
        }

        if (lowerMessage.contains("429") || lowerMessage.contains("rate limit")) {
            return "Service temporarily unavailable";
        }

        if (lowerMessage.contains("500") || lowerMessage.contains("internal server error")) {
            return "Request failed";
        }

        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return "Request timeout";
        }

        if (lowerMessage.contains("network") || lowerMessage.contains("connection")) {
            return "Connection error";
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
}
