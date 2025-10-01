package com.gosu.iconpackgenerator.domain.controller;

import com.gosu.iconpackgenerator.domain.controller.api.GalleryControllerAPI;
import com.gosu.iconpackgenerator.domain.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.service.DataInitializationService;
import com.gosu.iconpackgenerator.domain.service.GridCompositionService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GalleryController implements GalleryControllerAPI {

    private final GeneratedIconRepository generatedIconRepository;
    private final GridCompositionService gridCompositionService;

    @Override
    @GetMapping("/api/gallery/icons")
    @ResponseBody
    public ResponseEntity<List<GeneratedIcon>> getUserIcons(@AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedIcon> icons = generatedIconRepository.findByUserOrderByCreatedAtDesc(user);
            return ResponseEntity.ok(icons);
        } catch (Exception e) {
            log.error("Error retrieving user icons", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @GetMapping("/api/gallery/request/{requestId}")
    @ResponseBody
    public ResponseEntity<List<GeneratedIcon>> getRequestIcons(@PathVariable String requestId) {
        try {
            List<GeneratedIcon> icons = generatedIconRepository.findByRequestId(requestId);
            if (icons.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(icons);
        } catch (Exception e) {
            log.error("Error retrieving icons for request: {}", requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @GetMapping("/api/gallery/requests")
    @ResponseBody
    public ResponseEntity<List<String>> getUserRequestIds(@AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<String> requestIds = generatedIconRepository.findDistinctRequestIdsByUserOrderByCreatedAtDesc(user);
            return ResponseEntity.ok(requestIds);
        } catch (Exception e) {
            log.error("Error retrieving user request IDs", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @GetMapping("/api/gallery/icons/{iconType}")
    @ResponseBody
    public ResponseEntity<List<GeneratedIcon>> getUserIconsByType(@PathVariable String iconType, @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!"original".equals(iconType) && !"variation".equals(iconType)) {
                return ResponseEntity.badRequest().build();
            }

            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedIcon> icons = generatedIconRepository.findByUserAndIconTypeOrderByCreatedAtDesc(user, iconType);
            return ResponseEntity.ok(icons);
        } catch (Exception e) {
            log.error("Error retrieving {} icons", iconType, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @GetMapping("/api/gallery/request/{requestId}/{iconType}")
    @ResponseBody
    public ResponseEntity<List<GeneratedIcon>> getRequestIconsByType(@PathVariable String requestId, @PathVariable String iconType) {
        try {
            if (!"original".equals(iconType) && !"variation".equals(iconType)) {
                return ResponseEntity.badRequest().build();
            }

            List<GeneratedIcon> icons = generatedIconRepository.findByRequestIdAndIconType(requestId, iconType);
            if (icons.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(icons);
        } catch (Exception e) {
            log.error("Error retrieving {} icons for request: {}", iconType, requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/request/{requestId}")
    @ResponseBody
    public ResponseEntity<Void> deleteRequestIcons(@PathVariable String requestId) {
        try {
            List<GeneratedIcon> icons = generatedIconRepository.findByRequestId(requestId);
            if (icons.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            generatedIconRepository.deleteByRequestId(requestId);
            log.info("Deleted {} icons for request: {}", icons.size(), requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting icons for request: {}", requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Creates a 3x3 grid composition from first 9 icons of a specific request and icon type
     * This is used by the "Generate more" functionality
     */
    @PostMapping("/api/gallery/compose-grid")
    @ResponseBody
    public ResponseEntity<Map<String, String>> composeGrid(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            String requestId = (String) request.get("requestId");
            String iconType = (String) request.get("iconType");

            if (requestId == null || iconType == null) {
                return ResponseEntity.badRequest().build();
            }

            if (!"original".equals(iconType) && !"variation".equals(iconType)) {
                return ResponseEntity.badRequest().build();
            }

            // Get icons for this request and type
            List<GeneratedIcon> icons = generatedIconRepository.findByRequestIdAndIconType(requestId, iconType);
            
            if (icons.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Check if user owns these icons (security check)
            boolean userOwnsIcons = icons.stream().allMatch(icon -> 
                icon.getUser().getId().equals(user.getId()));
            
            if (!userOwnsIcons) {
                return ResponseEntity.status(403).build(); // Forbidden
            }

            // We need 9 icons for a 3x3 grid. If we have fewer, we'll repeat them to fill the grid.
            // If we have more, the first 9 will be used.
            List<String> iconPaths = icons.stream()
                    .map(GeneratedIcon::getFilePath)
                    .toList();

            List<String> gridIconPaths = new java.util.ArrayList<>();
            if (!iconPaths.isEmpty()) {
                for (int i = 0; i < 9; i++) {
                    gridIconPaths.add(iconPaths.get(i % iconPaths.size()));
                }
            }

            // Create the grid composition
            String gridImageBase64 = gridCompositionService.composeGrid(gridIconPaths);

            // Return the base64 image
            Map<String, String> response = new HashMap<>();
            response.put("gridImageBase64", gridImageBase64);
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating grid composition", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to create grid composition");
            errorResponse.put("status", "error");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
