package com.gosu.icon_pack_generator.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.util.Iterator;

@Configuration
@Slf4j
public class WebPConfig {
    
    @PostConstruct
    public void initWebPSupport() {
        try {
            log.info("Initializing WebP support using TwelveMonkeys ImageIO...");
            
            // Check if WebP readers are available
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                log.info("✅ WebP ImageReader found: {}", reader.getClass().getName());
                
                // Log additional details about the reader
                String[] suffixes = reader.getOriginatingProvider().getFileSuffixes();
                String[] mimeTypes = reader.getOriginatingProvider().getMIMETypes();
                
                log.info("WebP file suffixes: {}", String.join(", ", suffixes));
                log.info("WebP MIME types: {}", String.join(", ", mimeTypes));
                
                reader.dispose();
            } else {
                log.warn("⚠️ No WebP ImageReader found. WebP images may not be supported.");
            }
            
            // Log all available image formats
            String[] readerFormats = ImageIO.getReaderFormatNames();
            log.info("Available ImageIO reader formats: {}", String.join(", ", readerFormats));
            
            // Specifically check for webp in the list
            boolean webpSupported = false;
            for (String format : readerFormats) {
                if ("webp".equalsIgnoreCase(format)) {
                    webpSupported = true;
                    break;
                }
            }
            
            if (webpSupported) {
                log.info("✅ WebP format is supported by ImageIO");
            } else {
                log.warn("⚠️ WebP format not found in ImageIO supported formats");
            }
            
        } catch (Exception e) {
            log.error("Error initializing WebP support", e);
        }
    }
}
