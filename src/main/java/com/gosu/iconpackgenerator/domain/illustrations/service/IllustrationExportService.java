package com.gosu.iconpackgenerator.domain.illustrations.service;

import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationExportRequest;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse;
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
public class IllustrationExportService {
    
    /**
     * Create a ZIP file containing all illustrations in requested formats and sizes
     */
    public byte[] createIllustrationPackZip(IllustrationExportRequest exportRequest) throws Exception {
        log.info("Creating illustration pack ZIP for request: {} with {} illustrations, {} formats, {} sizes",
            exportRequest.getRequestId(), 
            exportRequest.getIllustrations() != null ? exportRequest.getIllustrations().size() : 0,
            exportRequest.getFormats() != null ? exportRequest.getFormats().size() : 0,
            exportRequest.getSizes() != null ? exportRequest.getSizes().size() : 0);
        
        List<String> formats = exportRequest.getFormats() != null && !exportRequest.getFormats().isEmpty() 
                ? exportRequest.getFormats() 
                : List.of("png", "webp");
        
        List<Integer> sizes = exportRequest.getSizes() != null && !exportRequest.getSizes().isEmpty()
                ? exportRequest.getSizes()
                : List.of(500);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            int illustrationIndex = 1;
            
            for (IllustrationGenerationResponse.GeneratedIllustration illustration : exportRequest.getIllustrations()) {
                // Decode base64 data
                byte[] originalImageData = Base64.getDecoder().decode(illustration.getBase64Data());
                BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalImageData));
                
                if (originalImage == null) {
                    log.warn("Could not read illustration {}, skipping", illustrationIndex);
                    illustrationIndex++;
                    continue;
                }
                
                // Process each size
                for (Integer width : sizes) {
                    // Calculate height for 5:4 ratio
                    int height = (width * 4) / 5;
                    
                    // Resize image
                    BufferedImage resizedImage = resizeImage(originalImage, width, height);
                    
                    // Process each format
                    for (String format : formats) {
                        String fileName = String.format("illustration_%d_%dx%d.%s", 
                                illustrationIndex, width, height, format);
                        
                        // Convert and add to ZIP
                        byte[] imageData = convertImageToFormat(resizedImage, format);
                        
                        ZipEntry entry = new ZipEntry(fileName);
                        zos.putNextEntry(entry);
                        zos.write(imageData);
                        zos.closeEntry();
                        
                        log.debug("Added illustration to ZIP: {}", fileName);
                    }
                }
                
                illustrationIndex++;
            }
            
            log.info("Successfully created illustration pack ZIP with {} illustrations in {} formats and {} sizes", 
                exportRequest.getIllustrations().size(), formats.size(), sizes.size());
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

