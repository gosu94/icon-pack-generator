package com.gosu.iconpackgenerator.domain.letters.service;

import com.gosu.iconpackgenerator.domain.icons.service.FileStorageService;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationResponse;
import com.gosu.iconpackgenerator.domain.letters.entity.GeneratedLetterIcon;
import com.gosu.iconpackgenerator.domain.letters.repository.GeneratedLetterIconRepository;
import com.gosu.iconpackgenerator.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LetterPackPersistenceService {

    private final GeneratedLetterIconRepository generatedLetterIconRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public void persistLetterPack(String requestId,
                                  LetterPackGenerationResponse response,
                                  User user,
                                  String theme) {
        if (response == null || response.getGroups() == null || response.getGroups().isEmpty()) {
            log.warn("No letter pack data to persist for request {}", requestId);
            return;
        }

        int sequenceCounter = 0;
        String groupKey = sanitizeGroupName("letters");
        String sanitizedTheme = (theme != null && !theme.trim().isEmpty()) ? theme.trim() : "Letter Pack";

        for (LetterPackGenerationResponse.LetterGroup group : response.getGroups()) {
            for (LetterPackGenerationResponse.LetterIcon icon : group.getIcons()) {
                try {
                    String fileName = fileStorageService.generateLetterIconFileName(icon.getLetter(), sequenceCounter);
                    String filePath = fileStorageService.saveLetterIcon(
                            user.getDirectoryPath(),
                            requestId,
                            groupKey,
                            fileName,
                            icon.getBase64Data()
                    );

                    GeneratedLetterIcon entity = new GeneratedLetterIcon();
                    entity.setRequestId(requestId);
                    entity.setLetter(icon.getLetter());
                    entity.setFileName(fileName);
                    entity.setFilePath(filePath);
                    entity.setLetterGroup(groupKey);
                    entity.setSequenceIndex(sequenceCounter);
                    entity.setFileSize(fileStorageService.getLetterFileSize(
                            user.getDirectoryPath(), requestId, groupKey, fileName));
                    entity.setUser(user);

                    entity.setTheme(sanitizedTheme);

                    generatedLetterIconRepository.save(entity);
                    sequenceCounter++;
                } catch (Exception e) {
                    log.error("Failed to persist letter icon {} for request {}", icon.getLetter(), requestId, e);
                }
            }
        }

        log.info("Persisted {} letter icons for request {}", sequenceCounter, requestId);
    }

    private String sanitizeGroupName(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return "group";
        }
        return groupName.replaceAll("[^a-zA-Z0-9\\-]", "_").toLowerCase();
    }
}
