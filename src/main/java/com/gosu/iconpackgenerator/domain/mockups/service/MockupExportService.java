package com.gosu.iconpackgenerator.domain.mockups.service;

import com.gosu.iconpackgenerator.domain.mockups.dto.MockupExportRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupGenerationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class MockupExportService {
    
    /**
     * Create a ZIP file containing all mockups in requested formats and sizes
     */
    public byte[] createMockupPackZip(MockupExportRequest exportRequest) throws Exception {
        log.info("Creating mockup pack ZIP for request: {} with {} mockups, {} formats, {} sizes",
            exportRequest.getRequestId(), 
            exportRequest.getMockups() != null ? exportRequest.getMockups().size() : 0,
            exportRequest.getFormats() != null ? exportRequest.getFormats().size() : 0,
            exportRequest.getSizes() != null ? exportRequest.getSizes().size() : 0);
        
        List<String> formats = exportRequest.getFormats() != null && !exportRequest.getFormats().isEmpty() 
                ? exportRequest.getFormats() 
                : List.of("png", "webp");
        
        // Default sizes for mockups are widths in pixels (height will be calculated for 16:9)
        List<Integer> sizes = exportRequest.getSizes();
        boolean useOriginalSize = sizes == null || sizes.isEmpty();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            int mockupIndex = 1;
            
            for (MockupGenerationResponse.GeneratedMockup mockup : exportRequest.getMockups()) {
                // Decode base64 data
                byte[] originalImageData = Base64.getDecoder().decode(mockup.getBase64Data());
                BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalImageData));
                
                if (originalImage == null) {
                    log.warn("Could not read mockup {}, skipping", mockupIndex);
                    mockupIndex++;
                    continue;
                }
                
                if (useOriginalSize) {
                    int width = originalImage.getWidth();
                    int height = originalImage.getHeight();
                    for (String format : formats) {
                        String fileName = String.format("mockup_%d_%dx%d.%s",
                                mockupIndex, width, height, format);
                        byte[] imageData = convertImageToFormat(originalImage, format);
                        ZipEntry entry = new ZipEntry(fileName);
                        zos.putNextEntry(entry);
                        zos.write(imageData);
                        zos.closeEntry();
                        log.debug("Added mockup to ZIP: {}", fileName);
                    }
                } else {
                    // Process each size
                    for (Integer width : sizes) {
                        int originalWidth = originalImage.getWidth();
                        int originalHeight = originalImage.getHeight();
                        int height = originalWidth > 0
                                ? (int) Math.round((double) originalHeight / originalWidth * width)
                                : width;
                        BufferedImage resizedImage = resizeImage(originalImage, width, height);
                        for (String format : formats) {
                            String fileName = String.format("mockup_%d_%dx%d.%s",
                                    mockupIndex, width, height, format);
                            byte[] imageData = convertImageToFormat(resizedImage, format);
                            ZipEntry entry = new ZipEntry(fileName);
                            zos.putNextEntry(entry);
                            zos.write(imageData);
                            zos.closeEntry();
                            log.debug("Added mockup to ZIP: {}", fileName);
                        }
                    }
                }
                
                mockupIndex++;
            }
            
            log.info("Successfully created mockup pack ZIP with {} mockups in {} formats and {} sizes", 
                exportRequest.getMockups().size(), formats.size(), sizes.size());
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Resize an image while maintaining aspect ratio (will be centered on canvas if needed)
     */
    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        // Enable high quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Fill with transparent background
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, targetWidth, targetHeight);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // Calculate scaling to fit within target dimensions
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        double scaleX = (double) targetWidth / originalWidth;
        double scaleY = (double) targetHeight / originalHeight;
        double scale = Math.min(scaleX, scaleY);
        
        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);
        
        // Center the image
        int x = (targetWidth - scaledWidth) / 2;
        int y = (targetHeight - scaledHeight) / 2;
        
        g2d.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return resizedImage;
    }
    
    /**
     * Convert a BufferedImage to the specified format
     */
    private byte[] convertImageToFormat(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Handle format conversion
        String imageFormat = format.equalsIgnoreCase("webp") ? "png" : format;
        ImageIO.write(image, imageFormat, baos);
        
        return baos.toByteArray();
    }
}
