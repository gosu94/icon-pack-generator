package com.gosu.iconpackgenerator.domain.service;

import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializationService implements CommandLineRunner {
    
    private final UserRepository userRepository;
    
    @Value("${app.file-storage.base-path}")
    private String baseStoragePath;
    
    private static final String DEFAULT_USER_EMAIL = "default@iconpack.com";
    private static final String DEFAULT_USER_PASSWORD = "defaultpassword123";
    
    @Override
    public void run(String... args) throws Exception {
        initializeDefaultUser();
        createUserDirectoryStructure();
    }
    
    private void initializeDefaultUser() {
        if (!userRepository.existsByEmail(DEFAULT_USER_EMAIL)) {
            User defaultUser = new User();
            defaultUser.setEmail(DEFAULT_USER_EMAIL);
            defaultUser.setPassword(DEFAULT_USER_PASSWORD); // In real app, this would be hashed
            defaultUser.setDirectoryPath("default-user");
            defaultUser.setIsActive(true);
            
            userRepository.save(defaultUser);
            log.info("Created default user with email: {}", DEFAULT_USER_EMAIL);
        } else {
            log.info("Default user already exists");
        }
    }
    
    private void createUserDirectoryStructure() {
        try {
            // Create base storage directory
            Path basePath = Paths.get(baseStoragePath);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
                log.info("Created base storage directory: {}", basePath.toAbsolutePath());
            }
            
            // Create default user directory
            Path defaultUserPath = basePath.resolve("default-user");
            if (!Files.exists(defaultUserPath)) {
                Files.createDirectories(defaultUserPath);
                log.info("Created default user directory: {}", defaultUserPath.toAbsolutePath());
            }
            
            // Create a sample request directory
            Path sampleRequestPath = defaultUserPath.resolve("sample-request");
            if (!Files.exists(sampleRequestPath)) {
                Files.createDirectories(sampleRequestPath);
                log.info("Created sample request directory: {}", sampleRequestPath.toAbsolutePath());
            }
            
        } catch (Exception e) {
            log.error("Error creating user directory structure", e);
        }
    }
    
    public User getDefaultUser() {
        return userRepository.findByEmail(DEFAULT_USER_EMAIL)
                .orElseThrow(() -> new RuntimeException("Default user not found"));
    }
}
