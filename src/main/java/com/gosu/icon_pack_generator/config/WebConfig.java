package com.gosu.icon_pack_generator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Value("${background-removal.output-dir}")
    private String outputDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // This configuration allows serving files directly from the filesystem
        // from the directory specified by 'background-removal.output-dir'.
        Path generatedImagesPath = Paths.get(outputDir);
        String absolutePath = generatedImagesPath.toFile().getAbsolutePath();
        String resourceLocation = generatedImagesPath.toUri().toString();
        String urlPath = generatedImagesPath.getFileName().toString();

        registry.addResourceHandler("/" + urlPath + "/**")
                .addResourceLocations(resourceLocation);

        log.info("Serving generated images from URL path '/{}/**' mapped to directory '{}'", urlPath, absolutePath);
    }
}