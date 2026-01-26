package com.gosu.iconpackgenerator.domain.uielements.service;

import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.mockups.entity.GeneratedMockup;
import com.gosu.iconpackgenerator.domain.mockups.repository.GeneratedMockupRepository;
import com.gosu.iconpackgenerator.domain.uielements.dto.UiElementGenerationRequest;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UiElementPersistenceService {

    private static final String UI_ELEMENT_TYPE = "elements";

    private final GeneratedMockupRepository generatedMockupRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public void persistGeneratedUiElements(String requestId,
                                           UiElementGenerationRequest request,
                                           IconGenerationResponse response,
                                           User user) {
        List<IconGenerationResponse.GeneratedIcon> icons = response.getIcons();
        if (icons == null || icons.isEmpty()) {
            return;
        }

        for (IconGenerationResponse.GeneratedIcon icon : icons) {
            if (icon.getBase64Data() == null || icon.getBase64Data().isBlank()) {
                continue;
            }
            persistSingleUiElement(requestId, icon, user);
        }
    }

    private void persistSingleUiElement(String requestId,
                                        IconGenerationResponse.GeneratedIcon icon,
                                        User user) {
        String fileName = fileStorageService.generateMockupFileName(icon.getId());
        String filePath = fileStorageService.saveMockup(
                user.getDirectoryPath(),
                requestId,
                UI_ELEMENT_TYPE,
                fileName,
                icon.getBase64Data()
        );

        GeneratedMockup generatedMockup = new GeneratedMockup();
        generatedMockup.setRequestId(requestId);
        generatedMockup.setMockupId(icon.getId());
        generatedMockup.setUser(user);
        generatedMockup.setFileName(fileName);
        generatedMockup.setFilePath(filePath);
        generatedMockup.setDescription(icon.getDescription());
        generatedMockup.setTheme("UI elements");
        generatedMockup.setGenerationIndex(1);
        generatedMockup.setMockupType(UI_ELEMENT_TYPE);

        long fileSize = fileStorageService.getMockupFileSize(
                user.getDirectoryPath(), requestId, UI_ELEMENT_TYPE, fileName);
        generatedMockup.setFileSize(fileSize);

        generatedMockupRepository.save(generatedMockup);
    }
}
