package com.gosu.iconpackgenerator.domain.backup.service;

import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.illustrations.repository.GeneratedIllustrationRepository;
import com.gosu.iconpackgenerator.domain.labels.repository.GeneratedLabelRepository;
import com.gosu.iconpackgenerator.domain.mockups.repository.GeneratedMockupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAssetBackupService {

    private final GeneratedIconRepository generatedIconRepository;
    private final GeneratedIllustrationRepository generatedIllustrationRepository;
    private final GeneratedLabelRepository generatedLabelRepository;
    private final GeneratedMockupRepository generatedMockupRepository;

    @Value("${app.backup-storage.base-path:static-backup}")
    private String backupBasePath;

    @Value("${app.file-storage.base-path:static/user-icons}")
    private String iconBasePath;

    @Value("${app.file-storage.private-base-path:static/user-icons-private}")
    private String privateIconBasePath;

    @Value("${app.illustrations-storage.base-path:static/user-illustrations}")
    private String illustrationBasePath;

    @Value("${app.illustrations-storage.private-base-path:static/user-illustrations-private}")
    private String privateIllustrationBasePath;

    @Value("${app.mockups-storage.base-path:static/user-mockups}")
    private String mockupBasePath;

    @Value("${app.labels-storage.base-path:static/user-labels}")
    private String labelBasePath;

    @Scheduled(cron = "${app.backup-storage.cron:0 0 2 * * *}")
    public void backupCustomerAssets() {
        Set<String> filePaths = collectCustomerFilePaths();
        if (filePaths.isEmpty()) {
            log.debug("Customer backup skipped: no assets found");
            return;
        }

        int copied = 0;
        int missing = 0;
        int failed = 0;

        for (String filePath : filePaths) {
            BackupPathResolution resolved = resolveFilePath(filePath);
            if (resolved == null) {
                continue;
            }

            try {
                Path targetPath = Paths.get(backupBasePath).resolve(resolved.relativePath());
                Files.createDirectories(targetPath.getParent());

                if (!Files.exists(resolved.sourcePath())) {
                    missing++;
                    continue;
                }

                Files.copy(resolved.sourcePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException e) {
                failed++;
                log.warn("Failed to backup asset {}", filePath, e);
            }
        }

        log.info("Daily customer asset backup finished. copied={}, missing={}, failed={}, total={}",
                copied, missing, failed, filePaths.size());
    }

    private Set<String> collectCustomerFilePaths() {
        Set<String> filePaths = new HashSet<>();
        addAllIfPresent(filePaths, generatedIconRepository.findDistinctFilePathsForCustomerUsers());
        addAllIfPresent(filePaths, generatedIllustrationRepository.findDistinctFilePathsForCustomerUsers());
        addAllIfPresent(filePaths, generatedLabelRepository.findDistinctFilePathsForCustomerUsers());
        addAllIfPresent(filePaths, generatedMockupRepository.findDistinctFilePathsForCustomerUsers());
        return filePaths;
    }

    private void addAllIfPresent(Set<String> filePaths, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                filePaths.add(candidate);
            }
        }
    }

    private BackupPathResolution resolveFilePath(String filePath) {
        if (filePath.startsWith("/user-icons/")) {
            return new BackupPathResolution(
                    Paths.get(iconBasePath).resolve(filePath.substring("/user-icons/".length())),
                    Paths.get("user-icons").resolve(filePath.substring("/user-icons/".length())));
        }
        if (filePath.startsWith("/private-icons/")) {
            return new BackupPathResolution(
                    Paths.get(privateIconBasePath).resolve(filePath.substring("/private-icons/".length())),
                    Paths.get("user-icons-private").resolve(filePath.substring("/private-icons/".length())));
        }
        if (filePath.startsWith("/user-illustrations/")) {
            return new BackupPathResolution(
                    Paths.get(illustrationBasePath).resolve(filePath.substring("/user-illustrations/".length())),
                    Paths.get("user-illustrations").resolve(filePath.substring("/user-illustrations/".length())));
        }
        if (filePath.startsWith("/private-illustrations/")) {
            return new BackupPathResolution(
                    Paths.get(privateIllustrationBasePath).resolve(filePath.substring("/private-illustrations/".length())),
                    Paths.get("user-illustrations-private").resolve(filePath.substring("/private-illustrations/".length())));
        }
        if (filePath.startsWith("/user-mockups/")) {
            return new BackupPathResolution(
                    Paths.get(mockupBasePath).resolve(filePath.substring("/user-mockups/".length())),
                    Paths.get("user-mockups").resolve(filePath.substring("/user-mockups/".length())));
        }
        if (filePath.startsWith("/user-labels/")) {
            return new BackupPathResolution(
                    Paths.get(labelBasePath).resolve(filePath.substring("/user-labels/".length())),
                    Paths.get("user-labels").resolve(filePath.substring("/user-labels/".length())));
        }

        log.debug("Skipping unsupported file path format during backup: {}", filePath);
        return null;
    }

    private record BackupPathResolution(Path sourcePath, Path relativePath) {
    }
}
