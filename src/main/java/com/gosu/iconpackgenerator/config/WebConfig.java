package com.gosu.iconpackgenerator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Value("${background-removal.output-dir}")
    private String outputDir;
    
    @Value("${app.file-storage.base-path}")
    private String fileStorageBasePath;

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

        // Serve static resources from classpath:/static/
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        // Serve static assets (CSS, JS, images) from Next.js build
        registry.addResourceHandler("/css/**", "/js/**", "/images/**", "/_next/**")
                .addResourceLocations("classpath:/static/css/", "classpath:/static/js/", 
                                     "classpath:/static/images/", "classpath:/static/_next/");

        // Ensure Swagger UI resources are properly served
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");

        // Serve user-generated icons from the file storage directory
        Path userIconsPath = Paths.get(fileStorageBasePath);
        String userIconsResourceLocation = userIconsPath.toUri().toString();
        registry.addResourceHandler("/user-icons/**")
                .addResourceLocations(userIconsResourceLocation)
                .setCachePeriod(3600); // Cache for 1 hour

        // Handle SPA routing - serve index.html for all routes that don't match API endpoints
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        
                        // If the requested resource exists, serve it
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        
                        // Try to find index.html in the requested directory (for Next.js routing)
                        if (!resourcePath.contains(".") && !resourcePath.startsWith("api/") && 
                            !resourcePath.startsWith("stream/") && !resourcePath.startsWith("export") && 
                            !resourcePath.startsWith("generate") && !resourcePath.startsWith("background-removal/") &&
                            !resourcePath.startsWith("swagger-ui/") && !resourcePath.startsWith("user-icons/")) {
                            
                            // First try: look for index.html in the requested path
                            String indexPath = resourcePath.endsWith("/") ? resourcePath + "index.html" : resourcePath + "/index.html";
                            Resource indexResource = location.createRelative(indexPath);
                            if (indexResource.exists() && indexResource.isReadable()) {
                                return indexResource;
                            }
                            
                            // Second try: if no trailing slash, try with trailing slash + index.html
                            if (!resourcePath.endsWith("/")) {
                                String trailingSlashIndexPath = resourcePath + "/index.html";
                                Resource trailingSlashIndexResource = location.createRelative(trailingSlashIndexPath);
                                if (trailingSlashIndexResource.exists() && trailingSlashIndexResource.isReadable()) {
                                    return trailingSlashIndexResource;
                                }
                            }
                            
                            // Fallback: serve root index.html for SPA routing
                            return new ClassPathResource("/static/index.html");
                        }
                        
                        return null;
                    }
                });

        log.info("Serving generated images from URL path '/{}/**' mapped to directory '{}'", urlPath, absolutePath);
//        log.info("Serving user icons from URL path '/user-icons/**' mapped to directory '{}'", userIconsPath.toFile().getAbsolutePath());
        log.info("Configured static content serving for Next.js SPA");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirect root to index.html
        registry.addRedirectViewController("/", "/index.html");
    }
}