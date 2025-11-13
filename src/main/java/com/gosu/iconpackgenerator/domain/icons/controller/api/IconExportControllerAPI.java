package com.gosu.iconpackgenerator.domain.icons.controller.api;

import com.gosu.iconpackgenerator.domain.icons.dto.GalleryExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.GifGalleryExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Tag(name = "Icon Export API", description = "Endpoints for exporting icons")
public interface IconExportControllerAPI {

    @Operation(summary = "Export icons as a ZIP file", description = "Creates and returns a ZIP file containing the generated icons.")
    @PostMapping("/export")
    @ResponseBody
    ResponseEntity<byte[]> exportIcons(@RequestBody IconExportRequest exportRequest,
                                       @AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Export selected icons from gallery as a ZIP file", description = "Creates and returns a ZIP file containing selected icons from the gallery.")
    @PostMapping("/api/export-gallery")
    @ResponseBody
    ResponseEntity<byte[]> exportFromGallery(@RequestBody GalleryExportRequest galleryExportRequest,
                                             @AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Export GIF files from gallery", description = "Downloads selected GIF files as either a single GIF or ZIP archive.")
    @PostMapping("/api/gallery/export-gifs")
    @ResponseBody
    ResponseEntity<byte[]> exportGifsFromGallery(@RequestBody GifGalleryExportRequest gifExportRequest,
                                                 @AuthenticationPrincipal OAuth2User principal);
}
