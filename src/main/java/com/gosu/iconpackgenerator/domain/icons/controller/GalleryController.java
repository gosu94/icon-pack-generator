package com.gosu.iconpackgenerator.domain.icons.controller;

import com.gosu.iconpackgenerator.domain.icons.controller.api.GalleryControllerAPI;
import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.icons.service.GridCompositionService;
import com.gosu.iconpackgenerator.domain.illustrations.entity.GeneratedIllustration;
import com.gosu.iconpackgenerator.domain.illustrations.repository.GeneratedIllustrationRepository;
import com.gosu.iconpackgenerator.domain.labels.entity.GeneratedLabel;
import com.gosu.iconpackgenerator.domain.labels.repository.GeneratedLabelRepository;
import com.gosu.iconpackgenerator.domain.mockups.entity.GeneratedMockup;
import com.gosu.iconpackgenerator.domain.mockups.repository.GeneratedMockupRepository;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import com.gosu.iconpackgenerator.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GalleryController implements GalleryControllerAPI {

    private final GeneratedIconRepository generatedIconRepository;
    private final GeneratedIllustrationRepository generatedIllustrationRepository;
    private final GeneratedMockupRepository generatedMockupRepository;
    private final GeneratedLabelRepository generatedLabelRepository;
    private final GridCompositionService gridCompositionService;
    private final FileStorageService fileStorageService;

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
            return ResponseEntity.ok(filterWatermarkedIcons(icons));
        } catch (Exception e) {
            log.error("Error retrieving user icons", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/api/gallery/labels")
    @ResponseBody
    public ResponseEntity<List<GeneratedLabel>> getUserLabels(@AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedLabel> labels = generatedLabelRepository.findByUserOrderByCreatedAtDesc(user);
            return ResponseEntity.ok(labels);
        } catch (Exception e) {
            log.error("Error retrieving user labels", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @GetMapping("/api/gallery/illustrations")
    @ResponseBody
    public ResponseEntity<List<GeneratedIllustration>> getUserIllustrations(@AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedIllustration> illustrations = generatedIllustrationRepository.findByUserOrderByCreatedAtDesc(user);
            return ResponseEntity.ok(filterWatermarkedIllustrations(illustrations));
        } catch (Exception e) {
            log.error("Error retrieving user illustrations", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/api/gallery/mockups")
    @ResponseBody
    public ResponseEntity<List<GeneratedMockup>> getUserMockups(@AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedMockup> mockups = generatedMockupRepository.findByUserOrderByCreatedAtDesc(user);
            return ResponseEntity.ok(mockups);
        } catch (Exception e) {
            log.error("Error retrieving user mockups", e);
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
            return ResponseEntity.ok(filterWatermarkedIcons(icons));
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
            return ResponseEntity.ok(filterWatermarkedIcons(icons));
        } catch (Exception e) {
            log.error("Error retrieving {} icons for request: {}", iconType, requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/request/{requestId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteRequestIcons(@PathVariable String requestId,
                                                   @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedIcon> icons = generatedIconRepository.findByUserAndRequestId(user, requestId);
            if (icons.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            for (GeneratedIcon icon : icons) {
                fileStorageService.deleteIconByRelativePath(icon.getFilePath());
            }
            generatedIconRepository.deleteByUserAndRequestId(user, requestId);
            log.info("Deleted {} icons for request: {}", icons.size(), requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting icons for request: {}", requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/request/{requestId}/{iconType}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteRequestIconsByType(@PathVariable String requestId,
                                                         @PathVariable String iconType,
                                                         @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!"original".equals(iconType) && !"variation".equals(iconType)) {
                return ResponseEntity.badRequest().build();
            }
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedIcon> icons = generatedIconRepository.findByUserAndRequestIdAndIconType(user, requestId, iconType);
            if (icons.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            for (GeneratedIcon icon : icons) {
                fileStorageService.deleteIconByRelativePath(icon.getFilePath());
            }
            generatedIconRepository.deleteByUserAndRequestIdAndIconType(user, requestId, iconType);
            log.info("Deleted {} {} icons for request: {}", icons.size(), iconType, requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting {} icons for request: {}", iconType, requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/illustrations/request/{requestId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteRequestIllustrations(@PathVariable String requestId,
                                                           @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedIllustration> illustrations = generatedIllustrationRepository.findByUserAndRequestId(user, requestId);
            if (illustrations.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            for (GeneratedIllustration illustration : illustrations) {
                fileStorageService.deleteIllustrationByRelativePath(illustration.getFilePath());
            }
            generatedIllustrationRepository.deleteByUserAndRequestId(user, requestId);
            log.info("Deleted {} illustrations for request: {}", illustrations.size(), requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting illustrations for request: {}", requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/illustrations/request/{requestId}/{illustrationType}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteRequestIllustrationsByType(@PathVariable String requestId,
                                                                 @PathVariable String illustrationType,
                                                                 @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!"original".equals(illustrationType) && !"variation".equals(illustrationType)) {
                return ResponseEntity.badRequest().build();
            }
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedIllustration> illustrations =
                    generatedIllustrationRepository.findByUserAndRequestIdAndIllustrationType(user, requestId, illustrationType);
            if (illustrations.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            for (GeneratedIllustration illustration : illustrations) {
                fileStorageService.deleteIllustrationByRelativePath(illustration.getFilePath());
            }
            generatedIllustrationRepository.deleteByUserAndRequestIdAndIllustrationType(user, requestId, illustrationType);
            log.info("Deleted {} {} illustrations for request: {}", illustrations.size(), illustrationType, requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting {} illustrations for request: {}", illustrationType, requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/mockups/request/{requestId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteRequestMockups(@PathVariable String requestId,
                                                     @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedMockup> mockups = generatedMockupRepository.findByUserAndRequestId(user, requestId);
            if (mockups.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            for (GeneratedMockup mockup : mockups) {
                fileStorageService.deleteMockupByRelativePath(mockup.getFilePath());
            }
            generatedMockupRepository.deleteByUserAndRequestId(user, requestId);
            log.info("Deleted {} mockups for request: {}", mockups.size(), requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting mockups for request: {}", requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/mockups/request/{requestId}/{mockupType}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteRequestMockupsByType(@PathVariable String requestId,
                                                           @PathVariable String mockupType,
                                                           @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!"original".equals(mockupType) && !"variation".equals(mockupType)) {
                return ResponseEntity.badRequest().build();
            }
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedMockup> mockups =
                    generatedMockupRepository.findByUserAndRequestIdAndMockupType(user, requestId, mockupType);
            if (mockups.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            for (GeneratedMockup mockup : mockups) {
                fileStorageService.deleteMockupByRelativePath(mockup.getFilePath());
            }
            generatedMockupRepository.deleteByUserAndRequestIdAndMockupType(user, requestId, mockupType);
            log.info("Deleted {} {} mockups for request: {}", mockups.size(), mockupType, requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting {} mockups for request: {}", mockupType, requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/labels/request/{requestId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteRequestLabels(@PathVariable String requestId,
                                                    @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedLabel> labels = generatedLabelRepository.findByUserAndRequestId(user, requestId);
            if (labels.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            for (GeneratedLabel label : labels) {
                fileStorageService.deleteLabelByRelativePath(label.getFilePath());
            }
            generatedLabelRepository.deleteByUserAndRequestId(user, requestId);
            log.info("Deleted {} labels for request: {}", labels.size(), requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting labels for request: {}", requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Override
    @DeleteMapping("/api/gallery/labels/request/{requestId}/{labelType}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteRequestLabelsByType(@PathVariable String requestId,
                                                          @PathVariable String labelType,
                                                          @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!"original".equals(labelType) && !"variation".equals(labelType)) {
                return ResponseEntity.badRequest().build();
            }
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            List<GeneratedLabel> labels = generatedLabelRepository.findByUserAndRequestIdAndLabelType(user, requestId, labelType);
            if (labels.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            for (GeneratedLabel label : labels) {
                fileStorageService.deleteLabelByRelativePath(label.getFilePath());
            }
            generatedLabelRepository.deleteByUserAndRequestIdAndLabelType(user, requestId, labelType);
            log.info("Deleted {} {} labels for request: {}", labels.size(), labelType, requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting {} labels for request: {}", labelType, requestId, e);
            return ResponseEntity.status(500).build();
        }
    }

    private List<GeneratedIcon> filterWatermarkedIcons(List<GeneratedIcon> icons) {
        Map<String, Boolean> hasWatermarkByGroup = new HashMap<>();
        for (GeneratedIcon icon : icons) {
            if (Boolean.TRUE.equals(icon.getIsWatermarked())) {
                hasWatermarkByGroup.put(buildIconWatermarkGroupKey(icon), true);
            }
        }

        List<GeneratedIcon> filtered = new java.util.ArrayList<>();
        for (GeneratedIcon icon : icons) {
            if (Boolean.TRUE.equals(icon.getIsWatermarked())
                    || !hasWatermarkByGroup.getOrDefault(buildIconWatermarkGroupKey(icon), false)) {
                filtered.add(icon);
            }
        }
        return filtered;
    }

    private String buildIconWatermarkGroupKey(GeneratedIcon icon) {
        String generationIndex = icon.getGenerationIndex() != null ? icon.getGenerationIndex().toString() : "1";
        String iconType = icon.getIconType() != null ? icon.getIconType() : "unknown";
        return icon.getRequestId() + "|" + iconType + "|" + generationIndex;
    }

    private List<GeneratedIllustration> filterWatermarkedIllustrations(List<GeneratedIllustration> illustrations) {
        Map<String, Boolean> hasWatermarkByGroup = new HashMap<>();
        for (GeneratedIllustration illustration : illustrations) {
            if (Boolean.TRUE.equals(illustration.getIsWatermarked())) {
                hasWatermarkByGroup.put(buildIllustrationWatermarkGroupKey(illustration), true);
            }
        }

        List<GeneratedIllustration> filtered = new java.util.ArrayList<>();
        for (GeneratedIllustration illustration : illustrations) {
            if (Boolean.TRUE.equals(illustration.getIsWatermarked())
                    || !hasWatermarkByGroup.getOrDefault(buildIllustrationWatermarkGroupKey(illustration), false)) {
                filtered.add(illustration);
            }
        }
        return filtered;
    }

    private String buildIllustrationWatermarkGroupKey(GeneratedIllustration illustration) {
        String generationIndex = illustration.getGenerationIndex() != null ? illustration.getGenerationIndex().toString() : "1";
        String illustrationType = illustration.getIllustrationType() != null ? illustration.getIllustrationType() : "unknown";
        return illustration.getRequestId() + "|" + illustrationType + "|" + generationIndex;
    }

    /**
     * Creates a 3x3 grid composition from first 9 icons of a specific request and icon type
     * This is used by the "Generate more" functionality
     * @Deprecated no longer needed (we are composing grid on frontend - delete it at some point)
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

    /**
     * Creates a 2x2 grid composition from first 4 illustrations of a specific request and illustration type
     * This is used by the "Generate more" functionality for illustrations
     */
    @PostMapping("/api/gallery/compose-illustration-grid")
    @ResponseBody
    public ResponseEntity<Map<String, String>> composeIllustrationGrid(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal OAuth2User principal) {
        try {
            if (!(principal instanceof CustomOAuth2User customUser)) {
                return ResponseEntity.status(401).build();
            }

            User user = customUser.getUser();
            String requestId = (String) request.get("requestId");
            String illustrationType = (String) request.get("illustrationType");

            if (requestId == null || illustrationType == null) {
                return ResponseEntity.badRequest().build();
            }

            if (!"original".equals(illustrationType) && !"variation".equals(illustrationType)) {
                return ResponseEntity.badRequest().build();
            }

            // Get illustrations for this request and type
            List<GeneratedIllustration> illustrations = generatedIllustrationRepository
                    .findByRequestIdAndIllustrationType(requestId, illustrationType);
            
            if (illustrations.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Check if user owns these illustrations (security check)
            boolean userOwnsIllustrations = illustrations.stream().allMatch(illustration -> 
                illustration.getUser().getId().equals(user.getId()));
            
            if (!userOwnsIllustrations) {
                return ResponseEntity.status(403).build(); // Forbidden
            }

            // We need 4 illustrations for a 2x2 grid. If we have fewer, we'll repeat them to fill the grid.
            // If we have more, the first 4 will be used.
            List<String> illustrationPaths = illustrations.stream()
                    .map(GeneratedIllustration::getFilePath)
                    .toList();

            List<String> gridIllustrationPaths = new java.util.ArrayList<>();
            if (!illustrationPaths.isEmpty()) {
                for (int i = 0; i < 4; i++) {
                    gridIllustrationPaths.add(illustrationPaths.get(i % illustrationPaths.size()));
                }
            }

            // Create the 2x2 grid composition for illustrations
            String gridImageBase64 = gridCompositionService.composeIllustrationGrid(gridIllustrationPaths);

            // Return the base64 image
            Map<String, String> response = new HashMap<>();
            response.put("gridImageBase64", gridImageBase64);
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating illustration grid composition", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to create illustration grid composition");
            errorResponse.put("status", "error");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
