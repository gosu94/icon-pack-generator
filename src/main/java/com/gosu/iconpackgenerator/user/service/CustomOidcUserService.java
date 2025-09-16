package com.gosu.iconpackgenerator.user.service;

import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@Profile("!test")
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    public CustomOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        try {
            OidcUser oidcUser = super.loadUser(userRequest);
            
            String email = oidcUser.getAttribute("email");
            String name = oidcUser.getAttribute("name");
            
            if (email == null) {
                throw new OAuth2AuthenticationException("Email not found from OIDC provider");
            }

            User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewUser(email, name));
            
            // Update last login
            user.setLastLogin(LocalDateTime.now());
            user = userRepository.save(user);
            
            log.info("User authenticated: {}", email);
            
            return new CustomOAuth2User(
                oidcUser.getAttributes(), 
                user, 
                oidcUser.getIdToken(), 
                oidcUser.getUserInfo()
            );
            
        } catch (Exception e) {
            log.error("Error during OIDC user loading", e);
            throw new OAuth2AuthenticationException("Failed to load OIDC user: " + e.getMessage());
        }
    }

    private User createNewUser(String email, String name) {
        try {
            User user = new User();
            user.setEmail(email);
            user.setPassword(""); // OAuth users don't need passwords
            String directoryPath = UUID.randomUUID().toString();
            user.setDirectoryPath(directoryPath); // Use random UUID for directory
            user.setIsActive(true);
            user.setCoins(0); // New users start with 0 coins
            user.setTrialCoins(1); // New users get 1 trial coin for first experience
            user.setRegisteredAt(LocalDateTime.now());
            
            user = userRepository.save(user);
            
            // Create user directory structure
            createUserDirectoryStructure(user.getDirectoryPath());
            
            log.info("Created new user: {} (ID: {}) with 0 regular coins and 1 trial coin", email, user.getId());
            
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
}
