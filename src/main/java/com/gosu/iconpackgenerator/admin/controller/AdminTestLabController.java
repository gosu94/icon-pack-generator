package com.gosu.iconpackgenerator.admin.controller;

import com.gosu.iconpackgenerator.admin.dto.AdminTestLabIconRequest;
import com.gosu.iconpackgenerator.admin.service.AdminService;
import com.gosu.iconpackgenerator.admin.service.AdminTestLabService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.icons.service.IconExportService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/admin/test-lab")
@RequiredArgsConstructor
@Slf4j
public class AdminTestLabController {

    private final AdminService adminService;
    private final AdminTestLabService adminTestLabService;
    private final IconExportService iconExportService;

    @PostMapping("/icons")
    public CompletableFuture<ResponseEntity<?>> generateIconComparisons(
            @Valid @RequestBody AdminTestLabIconRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!(principal instanceof CustomOAuth2User customUser)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(401).body(Map.of("error", "Unauthorized")));
        }

        User adminUser = customUser.getUser();
        if (!adminService.isAdmin(adminUser)) {
            log.warn("Non-admin user {} attempted to access test lab", adminUser.getEmail());
            return CompletableFuture.completedFuture(ResponseEntity.status(403).body(Map.of("error", "Forbidden - Admin access required")));
        }

        if (!request.isValid()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest()
                    .body(Map.of("error", "Either general description or reference image must be provided")));
        }

        return adminTestLabService.generateIconComparisons(request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/icons/export")
    public ResponseEntity<byte[]> exportIconComparisons(
            @RequestBody IconExportRequest exportRequest,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body("Unauthorized".getBytes(StandardCharsets.UTF_8));
        }

        User adminUser = customUser.getUser();
        if (!adminService.isAdmin(adminUser)) {
            log.warn("Non-admin user {} attempted to export test lab results", adminUser.getEmail());
            return ResponseEntity.status(403).body("Forbidden - Admin access required".getBytes(StandardCharsets.UTF_8));
        }

        if (exportRequest.getIcons() == null || exportRequest.getIcons().isEmpty()) {
            return ResponseEntity.badRequest().body("No icons provided for export".getBytes(StandardCharsets.UTF_8));
        }

        try {
            byte[] zipData = iconExportService.createIconPackZip(exportRequest);
            String requestId = exportRequest.getRequestId() != null ? exportRequest.getRequestId() : "test-lab";
            String serviceName = exportRequest.getServiceName() != null ? exportRequest.getServiceName() : "gpt";
            String fileName = "icon-pack-" + requestId + "-" + serviceName + "-gen" + exportRequest.getGenerationIndex() + ".zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);
        } catch (Exception e) {
            log.error("Failed to export test lab icons", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to export test lab icons".getBytes(StandardCharsets.UTF_8));
        }
    }
}
