package com.gosu.iconpackgenerator.domain.icons.controller.api;

import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Tag(name = "Gallery API", description = "Endpoints for browsing and managing generated icons")
public interface GalleryControllerAPI {

    @Operation(summary = "Get all generated icons for the authenticated user")
    @GetMapping("/api/gallery/icons")
    @ResponseBody
    ResponseEntity<List<GeneratedIcon>> getUserIcons(@AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Get all generated icons for a specific request")
    @GetMapping("/api/gallery/request/{requestId}")
    @ResponseBody
    ResponseEntity<List<GeneratedIcon>> getRequestIcons(@PathVariable String requestId);

    @Operation(summary = "Get distinct request IDs for the authenticated user")
    @GetMapping("/api/gallery/requests")
    @ResponseBody
    ResponseEntity<List<String>> getUserRequestIds(@AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Get icons by type (original or variation) for authenticated user")
    @GetMapping("/api/gallery/icons/{iconType}")
    @ResponseBody
    ResponseEntity<List<GeneratedIcon>> getUserIconsByType(@PathVariable String iconType, @AuthenticationPrincipal OAuth2User principal);

    @Operation(summary = "Get icons for a specific request and type")
    @GetMapping("/api/gallery/request/{requestId}/{iconType}")
    @ResponseBody
    ResponseEntity<List<GeneratedIcon>> getRequestIconsByType(@PathVariable String requestId, @PathVariable String iconType);

    @Operation(summary = "Delete icons for a specific request")
    @DeleteMapping("/api/gallery/request/{requestId}")
    @ResponseBody
    ResponseEntity<Void> deleteRequestIcons(@PathVariable String requestId);
}
