package com.gosu.iconpackgenerator.util;

/**
 * Utility for masking email addresses before logging or sending notifications.
 */
public final class EmailMasker {

    private EmailMasker() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return maskHalf(email);
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        if (localPart.isEmpty()) {
            return maskHalf(email);
        }

        String maskedLocal = maskHalf(localPart);
        return maskedLocal + domainPart;
    }

    private static String maskHalf(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return value;
        }

        int length = trimmed.length();
        int maskLength = Math.max(1, (int) Math.ceil(length / 2.0));
        int keepLength = Math.max(0, length - maskLength);

        String visiblePart = trimmed.substring(0, keepLength);
        String maskedPart = "*".repeat(maskLength);

        if (trimmed.equals(value)) {
            return visiblePart + maskedPart;
        }

        int prefixWhitespace = value.indexOf(trimmed);
        int suffixWhitespace = value.length() - prefixWhitespace - trimmed.length();
        return " ".repeat(Math.max(0, prefixWhitespace)) + visiblePart + maskedPart
                + " ".repeat(Math.max(0, suffixWhitespace));
    }
}
