package com.gosu.iconpackgenerator.domain.labels.controller;

import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService;
import com.gosu.iconpackgenerator.domain.icons.service.FileStorageService;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelExportRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGalleryExportRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationResponse;
import com.gosu.iconpackgenerator.domain.labels.entity.GeneratedLabel;
import com.gosu.iconpackgenerator.domain.labels.repository.GeneratedLabelRepository;
import com.gosu.iconpackgenerator.domain.labels.service.LabelExportService;
import com.gosu.iconpackgenerator.domain.vectorization.VectorizationException;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LabelExportController {

    private final LabelExportService labelExportService;
    private final StreamingStateStore streamingStateStore;
    private final GeneratedLabelRepository generatedLabelRepository;
    private final FileStorageService fileStorageService;
    private final CoinManagementService coinManagementService;

    @PostMapping("/api/labels/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportLabels(@RequestBody LabelExportRequest exportRequest,
                                               @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            log.warn("Unauthorized label export attempt: no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not authenticated".getBytes(StandardCharsets.UTF_8));
        }

        User user = customUser.getUser();
        List<LabelGenerationResponse.GeneratedLabel> labels = exportRequest.getLabels();

        if (labels == null || labels.isEmpty()) {
            Object stored = streamingStateStore.getResponse(exportRequest.getRequestId());
            if (stored instanceof LabelGenerationResponse labelResponse) {
                labels = new ArrayList<>();
                if (labelResponse.getGptResults() != null) {
                    for (LabelGenerationResponse.ServiceResults result : labelResponse.getGptResults()) {
                        if (result.getGenerationIndex() != null &&
                                result.getGenerationIndex() == exportRequest.getGenerationIndex() &&
                                result.getLabels() != null) {
                            labels.addAll(result.getLabels());
                        }
                    }
                }
            }
        }

        if (labels == null || labels.isEmpty()) {
            log.warn("No labels found for request {} generation {}", exportRequest.getRequestId(), exportRequest.getGenerationIndex());
            return ResponseEntity.notFound().build();
        }

        exportRequest.setLabels(labels);

        CoinManagementService.CoinDeductionResult coinResult = null;
        if (exportRequest.isVectorizeSvg()) {
            coinResult = coinManagementService.deductCoinsForGeneration(user, 1);
            if (!coinResult.isSuccess()) {
                String errorMessage = coinResult.getErrorMessage() != null
                        ? coinResult.getErrorMessage()
                        : "Insufficient coins for vectorized export.";
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(errorMessage.getBytes(StandardCharsets.UTF_8));
            }
            log.info("Vectorized label export coin deduction for user {}: deducted {} {} coin(s) (requestId={})",
                    user.getEmail(),
                    coinResult.getDeductedAmount(),
                    coinResult.isUsedTrialCoins() ? "trial" : "regular",
                    exportRequest.getRequestId());
        }

        try {
            byte[] zipData = labelExportService.createLabelPackZip(exportRequest);
            String fileName = String.format("label-pack-%s-gen%d.zip",
                    exportRequest.getRequestId(), exportRequest.getGenerationIndex());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);
        } catch (VectorizationException e) {
            log.error("Vectorized label export failed for request {}: {}", exportRequest.getRequestId(), e.getMessage());
            if (coinResult != null && coinResult.isSuccess()) {
                coinManagementService.refundCoins(user, coinResult.getDeductedAmount(), coinResult.isUsedTrialCoins());
            }
            String message = "Vectorization failed. Your coins were refunded. Please try again later.";
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error creating label export zip for request {}", exportRequest.getRequestId(), e);
            if (coinResult != null && coinResult.isSuccess()) {
                coinManagementService.refundCoins(user, coinResult.getDeductedAmount(), coinResult.isUsedTrialCoins());
            }
            return ResponseEntity.internalServerError()
                    .body("Failed to create label export".getBytes(StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/api/labels/export-gallery")
    @ResponseBody
    public ResponseEntity<byte[]> exportFromGallery(@RequestBody LabelGalleryExportRequest galleryRequest,
                                                    @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            log.warn("Unauthorized label gallery export attempt: no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not authenticated".getBytes(StandardCharsets.UTF_8));
        }

        User user = customUser.getUser();

        List<String> filePaths = galleryRequest.getLabelFilePaths();
        if (filePaths == null || filePaths.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<GeneratedLabel> storedLabels = generatedLabelRepository.findByFilePathIn(filePaths);
        if (storedLabels.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<LabelGenerationResponse.GeneratedLabel> labels = new ArrayList<>();
        for (GeneratedLabel storedLabel : storedLabels) {
            LabelGenerationResponse.GeneratedLabel label = new LabelGenerationResponse.GeneratedLabel();
            label.setId(storedLabel.getLabelId());
            label.setLabelText(storedLabel.getLabelText());
            label.setServiceSource(storedLabel.getServiceSource());

            try {
                byte[] data = fileStorageService.readLabel(storedLabel.getFilePath());
                label.setBase64Data(Base64.getEncoder().encodeToString(data));
            } catch (IOException e) {
                log.error("Failed to read label file {}", storedLabel.getFilePath(), e);
                continue;
            }

            labels.add(label);
        }

        if (labels.isEmpty()) {
            return ResponseEntity.internalServerError()
                    .body("Failed to load labels from storage".getBytes());
        }

        LabelExportRequest exportRequest = new LabelExportRequest();
        exportRequest.setRequestId("gallery-" + UUID.randomUUID().toString().substring(0, 8));
        exportRequest.setGenerationIndex(1);
        exportRequest.setServiceName("gallery");
        exportRequest.setFormats(galleryRequest.getFormats());
        exportRequest.setLabels(labels);
        exportRequest.setVectorizeSvg(galleryRequest.isVectorizeSvg());

        CoinManagementService.CoinDeductionResult coinResult = null;
        if (exportRequest.isVectorizeSvg()) {
            coinResult = coinManagementService.deductCoinsForGeneration(user, 1);
            if (!coinResult.isSuccess()) {
                String errorMessage = coinResult.getErrorMessage() != null
                        ? coinResult.getErrorMessage()
                        : "Insufficient coins for vectorized export.";
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(errorMessage.getBytes(StandardCharsets.UTF_8));
            }
            log.info("Gallery vectorized label export coin deduction for user {}: deducted {} {} coin(s) (fileCount={})",
                    user.getEmail(),
                    coinResult.getDeductedAmount(),
                    coinResult.isUsedTrialCoins() ? "trial" : "regular",
                    labels.size());
        }

        try {
            byte[] zipData = labelExportService.createLabelPackZip(exportRequest);
            String fileName = exportRequest.getRequestId() + "-labels.zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipData);
        } catch (VectorizationException e) {
            log.error("Vectorized label gallery export failed for user {}: {}", user.getEmail(), e.getMessage());
            if (coinResult != null && coinResult.isSuccess()) {
                coinManagementService.refundCoins(user, coinResult.getDeductedAmount(), coinResult.isUsedTrialCoins());
            }
            String message = "Vectorization failed for your labels. Coins were refunded. Please try again later.";
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error creating label export from gallery for user {}", user.getEmail(), e);
            if (coinResult != null && coinResult.isSuccess()) {
                coinManagementService.refundCoins(user, coinResult.getDeductedAmount(), coinResult.isUsedTrialCoins());
            }
            return ResponseEntity.internalServerError()
                    .body("Error creating label export".getBytes(StandardCharsets.UTF_8));
        }
    }
}
