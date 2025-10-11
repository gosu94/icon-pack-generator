package com.gosu.iconpackgenerator.domain.icons.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Service
@Slf4j
public class FileStorageService {
    
    @Value("${app.file-storage.base-path}")
    private String baseStoragePath;
    
    @Value("${app.illustrations-storage.base-path:static/user-illustrations}")
    private String illustrationsBasePath;
    
    @Value("${app.mockups-storage.base-path:static/user-mockups}")
    private String mockupsBasePath;
    
    /**
     * Save a base64 icon to the file system
     * @param userDirectoryPath The user's directory path (e.g., "default-user")
     * @param requestId The request ID to group icons
     * @param iconType The icon type ("original" or "variation")
     * @param fileName The file name (should include extension)
     * @param base64Data The base64 encoded image data
     * @return The full file path where the icon was saved
     */
    public String saveIcon(String userDirectoryPath, String requestId, String iconType, String fileName, String base64Data) {
        try {
            // Create the full directory path: baseStoragePath/userDirectoryPath/requestId/iconType
            Path directoryPath = Paths.get(baseStoragePath, userDirectoryPath, requestId, iconType);
            
            // Create directories if they don't exist
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                log.debug("Created directory: {}", directoryPath.toAbsolutePath());
            }
            
            // Create the full file path
            Path filePath = directoryPath.resolve(fileName);
            
            // Decode base64 and save to file
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            Files.write(filePath, imageBytes);
            
            log.debug("Saved icon to: {}", filePath.toAbsolutePath());
            
            // Return relative path from static resources root for web serving
            return getRelativeWebPath(userDirectoryPath, requestId, iconType, fileName);
            
        } catch (IOException e) {
            log.error("Error saving icon to file system", e);
            throw new RuntimeException("Failed to save icon: " + fileName, e);
        }
    }
    
    /**
     * Generate filename for an icon
     * @param serviceSource The AI service (e.g., "gpt", "flux")
     * @param iconId The icon ID
     * @param gridPosition The position in the grid (0-8)
     * @return The formatted filename
     */
    public String generateIconFileName(String serviceSource, String iconId, int gridPosition) {
        return String.format("%s_%s_%d.png", serviceSource, iconId.substring(0, 8), gridPosition);
    }
    
    /**
     * Get the relative web path for serving static files
     * This assumes the base storage path is under static resources
     */
    private String getRelativeWebPath(String userDirectoryPath, String requestId, String iconType, String fileName) {
        // For Docker environments, we need to serve files from the mounted volume
        // The web path should be /user-icons/userDirectoryPath/requestId/iconType/fileName
        return String.format("/user-icons/%s/%s/%s/%s", userDirectoryPath, requestId, iconType, fileName);
    }
    
    /**
     * Delete all files for a specific request
     */
    public void deleteRequestFiles(String userDirectoryPath, String requestId) {
        try {
            Path requestPath = Paths.get(baseStoragePath, userDirectoryPath, requestId);
            if (Files.exists(requestPath)) {
                Files.walk(requestPath)
                        .sorted((path1, path2) -> path2.compareTo(path1)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Error deleting file: {}", path, e);
                            }
                        });
                log.info("Deleted all files for request: {}", requestId);
            }
        } catch (IOException e) {
            log.error("Error deleting request files for: {}", requestId, e);
        }
    }
    
    /**
     * Get the file size of a saved icon
     */
    public long getFileSize(String userDirectoryPath, String requestId, String iconType, String fileName) {
        try {
            Path filePath = Paths.get(baseStoragePath, userDirectoryPath, requestId, iconType, fileName);
            if (Files.exists(filePath)) {
                return Files.size(filePath);
            }
        } catch (IOException e) {
            log.error("Error getting file size for: {}", fileName, e);
        }
        return 0L;
    }

    public byte[] readIcon(String relativeWebPath) throws IOException {
        // The relativeWebPath is like /user-icons/default-user/...
        // The baseStoragePath is like .../static/user-icons
        // We need to get the path relative to user-icons and join it with baseStoragePath.

        String pathInsideUserIcons;
        if (relativeWebPath.startsWith("/user-icons/")) {
            pathInsideUserIcons = relativeWebPath.substring("/user-icons/".length());
        } else {
            pathInsideUserIcons = relativeWebPath;
        }

        Path filePath = Paths.get(baseStoragePath).resolve(pathInsideUserIcons);
        if (Files.exists(filePath)) {
            return Files.readAllBytes(filePath);
        }
        throw new IOException("File not found: " + filePath.toString());
    }
    
    // ========== Illustration-specific methods ==========
    
    /**
     * Save a base64 illustration to the file system
     * @param userDirectoryPath The user's directory path (e.g., "default-user")
     * @param requestId The request ID to group illustrations
     * @param illustrationType The illustration type ("original" or "variation")
     * @param fileName The file name (should include extension)
     * @param base64Data The base64 encoded image data
     * @return The full file path where the illustration was saved
     */
    public String saveIllustration(String userDirectoryPath, String requestId, String illustrationType, 
                                   String fileName, String base64Data) {
        try {
            // Create the full directory path: illustrationsBasePath/userDirectoryPath/requestId/illustrationType
            Path directoryPath = Paths.get(illustrationsBasePath, userDirectoryPath, requestId, illustrationType);
            
            // Create directories if they don't exist
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                log.debug("Created illustration directory: {}", directoryPath.toAbsolutePath());
            }
            
            // Create the full file path
            Path filePath = directoryPath.resolve(fileName);
            
            // Decode base64 and save to file
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            Files.write(filePath, imageBytes);
            
            log.debug("Saved illustration to: {}", filePath.toAbsolutePath());
            
            // Return relative path from static resources root for web serving
            return getRelativeIllustrationWebPath(userDirectoryPath, requestId, illustrationType, fileName);
            
        } catch (IOException e) {
            log.error("Error saving illustration to file system", e);
            throw new RuntimeException("Failed to save illustration: " + fileName, e);
        }
    }
    
    /**
     * Generate filename for an illustration
     * @param illustrationId The illustration ID
     * @param gridPosition The position in the grid (0-3 for 2x2)
     * @return The formatted filename
     */
    public String generateIllustrationFileName(String illustrationId, int gridPosition) {
        return String.format("illustration_%s_%d.png", illustrationId.substring(0, 8), gridPosition);
    }
    
    /**
     * Get the relative web path for serving illustration files
     */
    private String getRelativeIllustrationWebPath(String userDirectoryPath, String requestId, 
                                                   String illustrationType, String fileName) {
        // The web path should be /user-illustrations/userDirectoryPath/requestId/illustrationType/fileName
        return String.format("/user-illustrations/%s/%s/%s/%s", 
                           userDirectoryPath, requestId, illustrationType, fileName);
    }
    
    /**
     * Get the file size of a saved illustration
     */
    public long getIllustrationFileSize(String userDirectoryPath, String requestId, 
                                       String illustrationType, String fileName) {
        try {
            Path filePath = Paths.get(illustrationsBasePath, userDirectoryPath, requestId, illustrationType, fileName);
            if (Files.exists(filePath)) {
                return Files.size(filePath);
            }
        } catch (IOException e) {
            log.error("Error getting file size for illustration: {}", fileName, e);
        }
        return 0L;
    }
    
    /**
     * Read illustration from file system
     */
    public byte[] readIllustration(String relativeWebPath) throws IOException {
        String pathInsideUserIllustrations;
        if (relativeWebPath.startsWith("/user-illustrations/")) {
            pathInsideUserIllustrations = relativeWebPath.substring("/user-illustrations/".length());
        } else {
            pathInsideUserIllustrations = relativeWebPath;
        }

        Path filePath = Paths.get(illustrationsBasePath).resolve(pathInsideUserIllustrations);
        if (Files.exists(filePath)) {
            return Files.readAllBytes(filePath);
        }
        throw new IOException("Illustration file not found: " + filePath.toString());
    }
    
    /**
     * Delete all illustration files for a specific request
     */
    public void deleteIllustrationRequestFiles(String userDirectoryPath, String requestId) {
        try {
            Path requestPath = Paths.get(illustrationsBasePath, userDirectoryPath, requestId);
            if (Files.exists(requestPath)) {
                Files.walk(requestPath)
                        .sorted((path1, path2) -> path2.compareTo(path1))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Error deleting illustration file: {}", path, e);
                            }
                        });
                log.info("Deleted all illustration files for request: {}", requestId);
            }
        } catch (IOException e) {
            log.error("Error deleting illustration request files for: {}", requestId, e);
        }
    }
    
    // ========== Mockup-specific methods ==========
    
    /**
     * Save a base64 mockup to the file system
     * @param userDirectoryPath The user's directory path (e.g., "default-user")
     * @param requestId The request ID to group mockups
     * @param mockupType The mockup type ("original" or "variation")
     * @param fileName The file name (should include extension)
     * @param base64Data The base64 encoded image data
     * @return The full file path where the mockup was saved
     */
    public String saveMockup(String userDirectoryPath, String requestId, String mockupType, 
                            String fileName, String base64Data) {
        try {
            // Create the full directory path: mockupsBasePath/userDirectoryPath/requestId/mockupType
            Path directoryPath = Paths.get(mockupsBasePath, userDirectoryPath, requestId, mockupType);
            
            // Create directories if they don't exist
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                log.debug("Created mockup directory: {}", directoryPath.toAbsolutePath());
            }
            
            // Create the full file path
            Path filePath = directoryPath.resolve(fileName);
            
            // Decode base64 and save to file
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            Files.write(filePath, imageBytes);
            
            log.debug("Saved mockup to: {}", filePath.toAbsolutePath());
            
            // Return relative path from static resources root for web serving
            return getRelativeMockupWebPath(userDirectoryPath, requestId, mockupType, fileName);
            
        } catch (IOException e) {
            log.error("Error saving mockup to file system", e);
            throw new RuntimeException("Failed to save mockup: " + fileName, e);
        }
    }
    
    /**
     * Generate filename for a mockup
     * @param mockupId The mockup ID
     * @return The formatted filename
     */
    public String generateMockupFileName(String mockupId) {
        return String.format("mockup_%s.png", mockupId.substring(0, 8));
    }
    
    /**
     * Get the relative web path for serving mockup files
     */
    private String getRelativeMockupWebPath(String userDirectoryPath, String requestId, 
                                           String mockupType, String fileName) {
        // The web path should be /user-mockups/userDirectoryPath/requestId/mockupType/fileName
        return String.format("/user-mockups/%s/%s/%s/%s", 
                           userDirectoryPath, requestId, mockupType, fileName);
    }
    
    /**
     * Get the file size of a saved mockup
     */
    public long getMockupFileSize(String userDirectoryPath, String requestId, 
                                 String mockupType, String fileName) {
        try {
            Path filePath = Paths.get(mockupsBasePath, userDirectoryPath, requestId, mockupType, fileName);
            if (Files.exists(filePath)) {
                return Files.size(filePath);
            }
        } catch (IOException e) {
            log.error("Error getting file size for mockup: {}", fileName, e);
        }
        return 0L;
    }
    
    /**
     * Read mockup from file system
     */
    public byte[] readMockup(String relativeWebPath) throws IOException {
        String pathInsideUserMockups;
        if (relativeWebPath.startsWith("/user-mockups/")) {
            pathInsideUserMockups = relativeWebPath.substring("/user-mockups/".length());
        } else {
            pathInsideUserMockups = relativeWebPath;
        }

        Path filePath = Paths.get(mockupsBasePath).resolve(pathInsideUserMockups);
        if (Files.exists(filePath)) {
            return Files.readAllBytes(filePath);
        }
        throw new IOException("Mockup file not found: " + filePath.toString());
    }
    
    /**
     * Delete all mockup files for a specific request
     */
    public void deleteMockupRequestFiles(String userDirectoryPath, String requestId) {
        try {
            Path requestPath = Paths.get(mockupsBasePath, userDirectoryPath, requestId);
            if (Files.exists(requestPath)) {
                Files.walk(requestPath)
                        .sorted((path1, path2) -> path2.compareTo(path1))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Error deleting mockup file: {}", path, e);
                            }
                        });
                log.info("Deleted all mockup files for request: {}", requestId);
            }
        } catch (IOException e) {
            log.error("Error deleting mockup request files for: {}", requestId, e);
        }
    }
}
