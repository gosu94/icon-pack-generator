package com.gosu.iconpackgenerator.domain.icons.controller.api;

import com.gosu.iconpackgenerator.domain.icons.dto.GalleryExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Tag(name = "Icon Export API", description = "Endpoints for exporting icons")
public interface IconExportControllerAPI {

    @Operation(summary = "Export icons as a ZIP file", description = "Creates and returns a ZIP file containing the generated icons.")
    @PostMapping("/export")
    @ResponseBody
    ResponseEntity<byte[]> exportIcons(@RequestBody IconExportRequest exportRequest);

    @Operation(summary = "Export selected icons from gallery as a ZIP file", description = "Creates and returns a ZIP file containing selected icons from the gallery.")
    @PostMapping("/api/export-gallery")
    @ResponseBody
    ResponseEntity<byte[]> exportFromGallery(@RequestBody GalleryExportRequest galleryExportRequest);
}
