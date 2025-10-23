package com.gosu.iconpackgenerator.domain.labels.controller;

import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.icons.service.FileStorageService;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelExportRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGalleryExportRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationResponse;
import com.gosu.iconpackgenerator.domain.labels.entity.GeneratedLabel;
import com.gosu.iconpackgenerator.domain.labels.repository.GeneratedLabelRepository;
import com.gosu.iconpackgenerator.domain.labels.service.LabelExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
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

    @PostMapping("/api/labels/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportLabels(@RequestBody LabelExportRequest exportRequest) {
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
    }

    @PostMapping("/api/labels/export-gallery")
    @ResponseBody
    public ResponseEntity<byte[]> exportFromGallery(@RequestBody LabelGalleryExportRequest galleryRequest) {
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

        byte[] zipData = labelExportService.createLabelPackZip(exportRequest);
        String fileName = exportRequest.getRequestId() + "-labels.zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(zipData.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(zipData);
    }
}

