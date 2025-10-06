package com.gosu.iconpackgenerator.domain.icons.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BackgroundRemovalService {
    
    @Value("${background-removal.enabled:true}")
    private boolean backgroundRemovalEnabled;
    
    @Value("${background-removal.rembg-command:rembg}")
    private String rembgCommand;
    
    @Value("${background-removal.timeout-seconds:30}")
    private long timeoutSeconds;
    
    @Value("${background-removal.model:u2net}")
    private String model;
    
    /**
     * Remove background from image data using rembg Python tool
     * @param imageData The original image as byte array
     * @return Image with background removed as byte array, or original image if removal fails
     */
    public byte[] removeBackground(byte[] imageData) {
        if (!backgroundRemovalEnabled) {
            log.debug("Background removal is disabled, returning original image");
            return imageData;
        }
        
        if (imageData == null || imageData.length == 0) {
            log.warn("Image data is null or empty, cannot remove background");
            return imageData;
        }
        
        log.info("Starting background removal with rembg, model: {}, image size: {} bytes", model, imageData.length);
        
        Path tempInputFile = null;
        Path tempOutputFile = null;
        
        try {
            // Create temporary files
            tempInputFile = Files.createTempFile("rembg_input_", ".png");
            tempOutputFile = Files.createTempFile("rembg_output_", ".png");
            
            // Write input image to temporary file
            Files.write(tempInputFile, imageData);
            log.debug("Wrote input image to temporary file: {}", tempInputFile);
            
            // Build rembg command
            String[] command = {
                rembgCommand,
                "i",
                "-m", model,
                tempInputFile.toString(),
                tempOutputFile.toString()
            };
            
            log.debug("Executing rembg command: {}", String.join(" ", command));
            
            // Execute rembg process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // Merge stderr with stdout
            
            Process process = processBuilder.start();
            
            // Capture output for debugging
            String output = captureProcessOutput(process);
            
            // Wait for process to complete with timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            
            if (!finished) {
                log.error("rembg process timed out after {} seconds", timeoutSeconds);
                process.destroyForcibly();
                return imageData;
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode != 0) {
                log.error("rembg process failed with exit code {}, output: {}", exitCode, output);
                return imageData;
            }
            
            // Check if output file exists and has content
            if (!Files.exists(tempOutputFile)) {
                log.error("rembg output file does not exist: {}", tempOutputFile);
                return imageData;
            }
            
            byte[] outputImageData = Files.readAllBytes(tempOutputFile);
            
            if (outputImageData.length == 0) {
                log.error("rembg output file is empty");
                return imageData;
            }
            
            log.info("Background removal successful, output size: {} bytes (reduction: {}%)", 
                    outputImageData.length, 
                    Math.round(100.0 * (imageData.length - outputImageData.length) / imageData.length));
            
            return outputImageData;
            
        } catch (IOException e) {
            log.error("IO error during background removal", e);
            return imageData;
        } catch (InterruptedException e) {
            log.error("Background removal process was interrupted", e);
            Thread.currentThread().interrupt();
            return imageData;
        } catch (Exception e) {
            log.error("Unexpected error during background removal", e);
            return imageData;
        } finally {
            // Clean up temporary files
            cleanupTempFile(tempInputFile);
            cleanupTempFile(tempOutputFile);
        }
    }
    
    /**
     * Check if rembg is available and working
     * @return true if rembg is available, false otherwise
     */
    public boolean isRembgAvailable() {
        if (!backgroundRemovalEnabled) {
            return false;
        }
        
        try {
            log.debug("Checking if rembg is available...");
            
            ProcessBuilder processBuilder = new ProcessBuilder(rembgCommand, "--help");
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                log.warn("rembg --help command timed out");
                return false;
            }
            
            int exitCode = process.exitValue();
            boolean available = exitCode == 0;
            
            log.info("rembg availability check: {} (exit code: {})", available ? "AVAILABLE" : "NOT AVAILABLE", exitCode);
            return available;
            
        } catch (Exception e) {
            log.warn("Error checking rembg availability: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get information about the background removal service configuration
     */
    public String getServiceInfo() {
        if (!backgroundRemovalEnabled) {
            return "Background removal is DISABLED";
        }
        
        boolean available = isRembgAvailable();
        return String.format("Background removal: %s, Command: %s, Model: %s, Timeout: %ds", 
                available ? "AVAILABLE" : "NOT AVAILABLE", 
                rembgCommand, 
                model, 
                timeoutSeconds);
    }
    
    private String captureProcessOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString().trim();
        } catch (IOException e) {
            log.warn("Error capturing process output", e);
            return "Unable to capture output";
        }
    }
    
    private void cleanupTempFile(Path file) {
        if (file != null && Files.exists(file)) {
            try {
                Files.delete(file);
                log.debug("Cleaned up temporary file: {}", file);
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", file, e);
            }
        }
    }
}
