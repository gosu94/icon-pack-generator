package com.gosu.iconpackgenerator.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
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
            
            // Check if WebP writers are available
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                log.info("✅ WebP ImageWriter found: {}", writer.getClass().getName());
                
                // Log additional details about the writer
                String[] suffixes = writer.getOriginatingProvider().getFileSuffixes();
                String[] mimeTypes = writer.getOriginatingProvider().getMIMETypes();
                
                log.info("WebP writer file suffixes: {}", String.join(", ", suffixes));
                log.info("WebP writer MIME types: {}", String.join(", ", mimeTypes));
                
                writer.dispose();
            } else {
                log.warn("⚠️ No WebP ImageWriter found. WebP export will not be available.");
            }
            
            // Log all available image formats
            String[] readerFormats = ImageIO.getReaderFormatNames();
            String[] writerFormats = ImageIO.getWriterFormatNames();
            log.info("Available ImageIO reader formats: {}", String.join(", ", readerFormats));
            log.info("Available ImageIO writer formats: {}", String.join(", ", writerFormats));
            
            // Specifically check for webp in the lists
            boolean webpReadSupported = false;
            boolean webpWriteSupported = false;
            
            for (String format : readerFormats) {
                if ("webp".equalsIgnoreCase(format)) {
                    webpReadSupported = true;
                    break;
                }
            }
            
            for (String format : writerFormats) {
                if ("webp".equalsIgnoreCase(format)) {
                    webpWriteSupported = true;
                    break;
                }
            }
            
            if (webpReadSupported) {
                log.info("✅ WebP reading is supported by ImageIO");
            } else {
                log.warn("⚠️ WebP reading not found in ImageIO supported formats");
            }
            
            if (webpWriteSupported) {
                log.info("✅ WebP writing is supported by ImageIO");
            } else {
                log.warn("⚠️ WebP writing not found in ImageIO supported formats");
            }
            
        } catch (Exception e) {
            log.error("Error initializing WebP support", e);
        }
    }
}
