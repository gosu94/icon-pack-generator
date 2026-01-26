package com.gosu.iconpackgenerator.domain.uielements.controller;

import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.icons.service.IconExportService;
import com.gosu.iconpackgenerator.domain.mockups.entity.GeneratedMockup;
import com.gosu.iconpackgenerator.domain.mockups.repository.GeneratedMockupRepository;
import com.gosu.iconpackgenerator.domain.uielements.dto.UiElementGalleryExportRequest;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import com.gosu.iconpackgenerator.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UiElementExportController {

    private final IconExportService iconExportService;
    private final GeneratedMockupRepository generatedMockupRepository;
    private final FileStorageService fileStorageService;
    private final CoinManagementService coinManagementService;

    @PostMapping("/api/ui-elements/export-gallery")
    @ResponseBody
    public ResponseEntity<byte[]> exportFromGallery(@RequestBody UiElementGalleryExportRequest exportRequest,
                                                    @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not authenticated".getBytes(StandardCharsets.UTF_8));
        }

        User user = customUser.getUser();
        List<String> filePaths = exportRequest.getUiElementFilePaths();
        if (filePaths == null || filePaths.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<GeneratedMockup> foundElements = generatedMockupRepository.findByFilePathIn(filePaths);
        List<IconGenerationResponse.GeneratedIcon> iconsToExport = new ArrayList<>();

        for (GeneratedMockup element : foundElements) {
            try {
                byte[] data = fileStorageService.readMockup(element.getFilePath());
                IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
                icon.setId(element.getMockupId());
                icon.setBase64Data(Base64.getEncoder().encodeToString(data));
                icon.setDescription(element.getDescription());
                icon.setGridPosition(0);
                icon.setServiceSource("gpt15");
                iconsToExport.add(icon);
            } catch (Exception e) {
                log.error("Failed to read UI element file {}", element.getFilePath(), e);
            }
        }

        if (iconsToExport.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int iconCount = Math.max(iconsToExport.size(), 1);
        int vectorCoinCost = exportRequest.isVectorizeSvg() ? (int) Math.ceil(iconCount / 9.0) : 0;
        int hqCoinCost = exportRequest.isHqUpscale() ? (int) Math.ceil(iconCount / 9.0) : 0;
        int totalCoinCost = vectorCoinCost + hqCoinCost;

        if (totalCoinCost > 0) {
            CoinManagementService.CoinDeductionResult coinResult =
                    coinManagementService.deductCoinsForGeneration(user, totalCoinCost);
            if (!coinResult.isSuccess()) {
                String errorMessage = coinResult.getErrorMessage() != null
                        ? coinResult.getErrorMessage()
                        : "Insufficient coins for premium export options.";
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(errorMessage.getBytes(StandardCharsets.UTF_8));
            }
        }

        IconExportRequest iconExportRequest = new IconExportRequest();
        iconExportRequest.setIcons(iconsToExport);
        iconExportRequest.setRequestId("ui-elements-gallery-" + UUID.randomUUID().toString().substring(0, 8));
        iconExportRequest.setServiceName("ui-elements");
        iconExportRequest.setGenerationIndex(1);
        iconExportRequest.setFormats(exportRequest.getFormats());
        iconExportRequest.setVectorizeSvg(exportRequest.isVectorizeSvg());
        iconExportRequest.setHqUpscale(exportRequest.isHqUpscale());
        iconExportRequest.setMinSvgSize(256);

        byte[] zipData = iconExportService.createIconPackZip(iconExportRequest);
        String fileName = iconExportRequest.getRequestId() + ".zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(zipData.length);

        return ResponseEntity.ok().headers(headers).body(zipData);
    }
}
