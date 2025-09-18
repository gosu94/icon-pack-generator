package com.gosu.iconpackgenerator.auth.service;

import com.gosu.iconpackgenerator.email.service.EmailService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAuthService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Check if email exists and if user has password set
     */
    public EmailCheckResult checkEmail(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isEmpty()) {
            return EmailCheckResult.EMAIL_NOT_FOUND;
        }
        
        User user = userOpt.get();
        
        // Check if user has a password set (not OAuth user or password is empty)
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return EmailCheckResult.NO_PASSWORD_SET;
        }
        
        return EmailCheckResult.PASSWORD_REQUIRED;
    }

    /**
     * Authenticate user with email and password
     */
    public Optional<User> authenticateUser(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isEmpty()) {
            log.warn("Authentication failed: User not found for email {}", email);
            return Optional.empty();
        }
        
        User user = userOpt.get();
        
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            log.warn("Authentication failed: No password set for user {}", email);
            return Optional.empty();
        }
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Authentication failed: Invalid password for user {}", email);
            return Optional.empty();
        }
        
        // Update last login
        user.setLastLogin(LocalDateTime.now());
        user = userRepository.save(user);
        
        log.info("User authenticated successfully: {}", email);
        return Optional.of(user);
    }

    /**
     * Send password setup email for new user or existing user without password
     */
    @Transactional
    public boolean sendPasswordSetupEmail(String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            User user;
            
            if (userOpt.isEmpty()) {
                // Create new user
                user = createNewUser(email);
            } else {
                user = userOpt.get();
                // Check if user already has password set
                if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
                    log.warn("Password setup email requested for user {} who already has password", email);
                    return false;
                }
            }
            
            // Generate verification token
            String token = UUID.randomUUID().toString();
            user.setEmailVerificationToken(token);
            user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(24)); // Token expires in 24 hours
            userRepository.save(user);
            
            // Send email
            boolean emailSent = emailService.sendPasswordSetupEmail(email, token);
            if (!emailSent) {
                log.error("Failed to send password setup email to {}", email);
                return false;
            }
            
            log.info("Password setup email sent successfully to {}", email);
            return true;
            
        } catch (Exception e) {
            log.error("Error sending password setup email to {}", email, e);
            return false;
        }
    }

    /**
     * Send password reset email for existing user
     */
    @Transactional
    public boolean sendPasswordResetEmail(String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            
            if (userOpt.isEmpty()) {
                log.warn("Password reset requested for non-existent user: {}", email);
                return false; // Don't reveal if email exists
            }
            
            User user = userOpt.get();
            
            // Check if user has password (not OAuth-only user)
            if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                log.warn("Password reset requested for OAuth-only user: {}", email);
                return false;
            }
            
            // Generate reset token
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(24)); // Token expires in 24 hours
            userRepository.save(user);
            
            // Send email
            boolean emailSent = emailService.sendPasswordResetEmail(email, token);
            if (!emailSent) {
                log.error("Failed to send password reset email to {}", email);
                return false;
            }
            
            log.info("Password reset email sent successfully to {}", email);
            return true;
            
        } catch (Exception e) {
            log.error("Error sending password reset email to {}", email, e);
            return false;
        }
    }

    /**
     * Verify token and set password
     */
    @Transactional
    public boolean setPassword(String token, String password, boolean isReset) {
        try {
            Optional<User> userOpt;
            
            if (isReset) {
                userOpt = userRepository.findByPasswordResetToken(token);
            } else {
                userOpt = userRepository.findByEmailVerificationToken(token);
            }
            
            if (userOpt.isEmpty()) {
                log.warn("Invalid token provided for password setup/reset: {}", token);
                return false;
            }
            
            User user = userOpt.get();
            
            if (user.getPasswordResetTokenExpiry() == null ||
                user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
                log.warn("Expired token used for password setup/reset: {}", token);
                return false;
            }
            
            String hashedPassword = passwordEncoder.encode(password);
            user.setPassword(hashedPassword);
            user.setEmailVerified(true);
            user.setAuthProvider("EMAIL");
            
            user.setEmailVerificationToken(null);
            user.setPasswordResetToken(null);
            user.setPasswordResetTokenExpiry(null);
            
            userRepository.save(user);
            
            log.info("Password set successfully for user {}", user.getEmail());
            return true;
            
        } catch (Exception e) {
            log.error("Error setting password for token {}", token, e);
            return false;
        }
    }

    /**
     * Validate token (for frontend to check if token is valid)
     */
    public boolean validateToken(String token, boolean isReset) {
        try {
            Optional<User> userOpt;
            
            if (isReset) {
                userOpt = userRepository.findByPasswordResetToken(token);
            } else {
                userOpt = userRepository.findByEmailVerificationToken(token);
            }
            
            if (userOpt.isEmpty()) {
                return false;
            }
            
            User user = userOpt.get();
            
            return user.getPasswordResetTokenExpiry() != null &&
                   user.getPasswordResetTokenExpiry().isAfter(LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error validating token {}", token, e);
            return false;
        }
    }

    private User createNewUser(String email) {
        try {
            User user = new User();
            user.setEmail(email);
            user.setPassword(""); // Will be set when user sets up password
            String directoryPath = UUID.randomUUID().toString();
            user.setDirectoryPath(directoryPath);
            user.setIsActive(true);
            user.setCoins(0);
            user.setTrialCoins(1); // New users get 1 trial coin
            user.setEmailVerified(false);
            user.setAuthProvider("EMAIL");
            user.setRegisteredAt(LocalDateTime.now());
            
            user = userRepository.save(user);
            
            // Create user directory structure
            createUserDirectoryStructure(user.getDirectoryPath());
            
            log.info("Created new user for email password setup: {} (ID: {})", email, user.getId());
            
            return user;
        } catch (Exception e) {
            log.error("Failed to create new user for email: {}", email, e);
            throw new RuntimeException("Failed to create user", e);
        }
    }

    private void createUserDirectoryStructure(String userDirectoryPath) {
        try {
            String baseStoragePath = "static/user-icons";
            Path userPath = Paths.get(baseStoragePath, userDirectoryPath);
            if (!Files.exists(userPath)) {
                Files.createDirectories(userPath);
            }
        } catch (Exception e) {
            log.error("Error creating user directory for: {}", userDirectoryPath, e);
        }
    }

    /**
     * Change password for authenticated user
     */
    @Transactional
    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (userOpt.isEmpty()) {
                log.error("User with ID {} not found for password change", userId);
                return false;
            }
            
            User user = userOpt.get();
            
            // Check if user has email authentication (not OAuth-only)
            if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                log.warn("Password change attempted for OAuth-only user: {}", userId);
                return false;
            }
            
            // Verify current password
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                log.warn("Invalid current password provided for user: {}", userId);
                return false;
            }
            
            // Hash and set new password
            String hashedNewPassword = passwordEncoder.encode(newPassword);
            user.setPassword(hashedNewPassword);
            userRepository.save(user);
            
            log.info("Password changed successfully for user: {}", userId);
            return true;
            
        } catch (Exception e) {
            log.error("Error changing password for user: {}", userId, e);
            return false;
        }
    }

    public enum EmailCheckResult {
        EMAIL_NOT_FOUND,
        NO_PASSWORD_SET,
        PASSWORD_REQUIRED
    }
}
