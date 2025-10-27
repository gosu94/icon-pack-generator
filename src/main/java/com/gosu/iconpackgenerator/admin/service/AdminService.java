package com.gosu.iconpackgenerator.admin.service;

import com.gosu.iconpackgenerator.user.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminService {

    @Value("${app.admin.email:}")
    private String adminEmail;

    /**
     * Check if the given email is an admin
     */
    public boolean isAdmin(String email) {
        if (adminEmail == null || adminEmail.trim().isEmpty()) {
            log.warn("Admin email not configured in application properties");
            return false;
        }
        
        boolean isAdmin = adminEmail.equalsIgnoreCase(email);
        log.debug("Admin check for email {}: {}", email, isAdmin);
        return isAdmin;
    }

    public String getConfiguredAdminEmail() {
        if (adminEmail == null) {
            return null;
        }
        String trimmed = adminEmail.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Check if the given user is an admin
     */
    public boolean isAdmin(User user) {
        if (user == null || user.getEmail() == null) {
            return false;
        }
        return isAdmin(user.getEmail());
    }
}
