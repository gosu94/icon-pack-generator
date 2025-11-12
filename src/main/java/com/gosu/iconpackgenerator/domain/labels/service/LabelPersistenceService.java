package com.gosu.iconpackgenerator.domain.labels.service;

import com.gosu.iconpackgenerator.util.FileStorageService;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationRequest;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelGenerationResponse;
import com.gosu.iconpackgenerator.domain.labels.entity.GeneratedLabel;
import com.gosu.iconpackgenerator.domain.labels.repository.GeneratedLabelRepository;
import com.gosu.iconpackgenerator.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabelPersistenceService {

    private final GeneratedLabelRepository generatedLabelRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public void persistGeneratedLabels(String requestId, LabelGenerationRequest request,
                                       LabelGenerationResponse response, User user) {
        if (response.getLabels() == null || response.getLabels().isEmpty()) {
            return;
        }

        log.info("Persisting {} labels for request {}", response.getLabels().size(), requestId);
        response.getLabels().forEach(label -> persistSingleLabel(requestId, request, label, user));
    }

    private void persistSingleLabel(String requestId, LabelGenerationRequest request,
                                    LabelGenerationResponse.GeneratedLabel label, User user) {
        Integer generationIndex = label.getGenerationIndex() != null ? label.getGenerationIndex() : 1;
        String labelType = generationIndex == 1 ? "original" : "variation";

        String fileName = fileStorageService.generateLabelFileName(
                label.getServiceSource(), label.getId(), generationIndex);

        String filePath = fileStorageService.saveLabel(
                user.getDirectoryPath(),
                requestId,
                labelType,
                fileName,
                label.getBase64Data()
        );

        GeneratedLabel entity = new GeneratedLabel();
        entity.setRequestId(requestId);
        entity.setLabelId(label.getId());
        entity.setUser(user);
        entity.setFileName(fileName);
        entity.setFilePath(filePath);
        entity.setServiceSource(label.getServiceSource());
        entity.setGenerationIndex(generationIndex);
        entity.setLabelText(label.getLabelText());
        entity.setTheme(request.getGeneralTheme());
        entity.setLabelType(labelType);

        long fileSize = fileStorageService.getLabelFileSize(
                user.getDirectoryPath(), requestId, labelType, fileName);
        entity.setFileSize(fileSize);

        generatedLabelRepository.save(entity);
    }
}

