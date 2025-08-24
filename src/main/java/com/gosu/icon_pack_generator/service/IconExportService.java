package com.gosu.icon_pack_generator.service;

import com.gosu.icon_pack_generator.dto.IconExportRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class IconExportService {

    private final ImageProcessingService imageProcessingService;

    public byte[] createIconPackZip(IconExportRequest exportRequest) {
        boolean removeBackground = exportRequest.isRemoveBackground();
        String outputFormat = exportRequest.getOutputFormat() != null ? exportRequest.getOutputFormat() : "png";
        log.info("Creating icon pack ZIP for request: {} (background removal: {}, format: {})",
                exportRequest.getRequestId(), removeBackground, outputFormat);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            int iconIndex = 1;
            for (IconGenerationResponse.GeneratedIcon icon : exportRequest.getIcons()) {
                String iconBase64Data = icon.getBase64Data();
                if (removeBackground) {
                    log.debug("Removing background from icon {} before adding to ZIP", iconIndex);
                    iconBase64Data = imageProcessingService.removeBackgroundFromIcon(iconBase64Data);
                }

                byte[] iconData = Base64.getDecoder().decode(iconBase64Data);
                String fileName = createFileName(icon, iconIndex, outputFormat);

                if ("svg".equalsIgnoreCase(outputFormat)) {
                    // Embed PNG in SVG
                    try {
                        ByteArrayInputStream bais = new ByteArrayInputStream(iconData);
                        BufferedImage image = ImageIO.read(bais);
                        int width = image.getWidth();
                        int height = image.getHeight();

                        String svgContent = """
                            <svg width="%d" height="%d" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
                                <image xlink:href="data:image/png;base64,%s" width="%d" height="%d"/>
                            </svg>
                            """.formatted(width, height, iconBase64Data, width, height);
                        iconData = svgContent.getBytes();
                    } catch (IOException e) {
                        log.error("Failed to read image dimensions for SVG conversion, skipping icon {}", icon.getId(), e);
                        continue;
                    }
                }

                ZipEntry zipEntry = new ZipEntry(fileName);
                zos.putNextEntry(zipEntry);
                zos.write(iconData);
                zos.closeEntry();

                log.debug("Added icon {} to ZIP: {} (format: {})", iconIndex, fileName, outputFormat);
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

    private String createFileName(IconGenerationResponse.GeneratedIcon icon, int index, String format) {
        String description = icon.getDescription();
        String extension = "png";
        if ("svg".equalsIgnoreCase(format)) {
            extension = "svg";
        }

        if (description != null && !description.trim().isEmpty() &&
            !description.startsWith("Generated icon") && !description.startsWith("Icon ")) {
            String sanitized = description.trim()
                    .replaceAll("[^a-zA-Z0-9\s-_]", "")
                    .replaceAll("\s+", "_")
                    .toLowerCase();

            if (sanitized.length() > 30) {
                sanitized = sanitized.substring(0, 30);
            }

            return String.format("%02d_%s.%s", index, sanitized, extension);
        } else {
            return String.format("%02d_icon.%s", index, extension);
        }
    }
}