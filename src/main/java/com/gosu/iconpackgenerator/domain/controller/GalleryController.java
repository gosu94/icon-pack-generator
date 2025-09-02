package com.gosu.iconpackgenerator.domain.controller;

import com.gosu.iconpackgenerator.domain.controller.api.GalleryControllerAPI;
import com.gosu.iconpackgenerator.domain.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.service.DataInitializationService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GalleryController implements GalleryControllerAPI {

    private final GeneratedIconRepository generatedIconRepository;
    private final DataInitializationService dataInitializationService;

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
}
