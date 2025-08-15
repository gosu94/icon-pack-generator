package com.gosu.icon_pack_generator.service;

import com.gosu.icon_pack_generator.dto.IconExportRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class IconExportService {
    
    /**
     * Create a ZIP file containing all the generated icons as PNG files
     * @param exportRequest The export request containing icons data
     * @return byte array representing the ZIP file
     */
    public byte[] createIconPackZip(IconExportRequest exportRequest) {
        log.info("Creating icon pack ZIP for request: {}", exportRequest.getRequestId());
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            int iconIndex = 1;
            for (IconGenerationResponse.GeneratedIcon icon : exportRequest.getIcons()) {
                String fileName = createFileName(icon, iconIndex);
                byte[] iconData = Base64.getDecoder().decode(icon.getBase64Data());
                
                ZipEntry zipEntry = new ZipEntry(fileName);
                zos.putNextEntry(zipEntry);
                zos.write(iconData);
                zos.closeEntry();
                
                log.debug("Added icon {} to ZIP: {}", iconIndex, fileName);
                iconIndex++;
            }
            
            zos.finish();
            log.info("Successfully created ZIP file with {} icons", exportRequest.getIcons().size());
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("Error creating ZIP file for request: {}", exportRequest.getRequestId(), e);
            throw new RuntimeException("Failed to create icon pack ZIP", e);
        }
    }
    
    private String createFileName(IconGenerationResponse.GeneratedIcon icon, int index) {
        String description = icon.getDescription();
        
        if (description != null && !description.trim().isEmpty() && 
            !description.startsWith("Generated icon") && !description.startsWith("Icon ")) {
            // Use description as filename, sanitized
            String sanitized = description.trim()
                    .replaceAll("[^a-zA-Z0-9\\s-_]", "") // Remove special characters
                    .replaceAll("\\s+", "_") // Replace spaces with underscores
                    .toLowerCase();
            
            if (sanitized.length() > 30) {
                sanitized = sanitized.substring(0, 30);
            }
            
            return String.format("%02d_%s.png", index, sanitized);
        } else {
            // Fallback to generic naming
            return String.format("%02d_icon.png", index);
        }
    }
}
