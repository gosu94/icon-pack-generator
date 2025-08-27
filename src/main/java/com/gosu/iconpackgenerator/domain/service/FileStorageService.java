package com.gosu.iconpackgenerator.domain.service;

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
        // Construct the full path by resolving the relative path against the base storage path.
        // The relative path starts with "/", so we need to remove it before resolving.
        Path filePath = Paths.get(baseStoragePath).resolve(relativeWebPath.substring(1));
        if (Files.exists(filePath)) {
            return Files.readAllBytes(filePath);
        }
        throw new IOException("File not found: " + relativeWebPath);
    }
}
