package com.gosu.iconpackgenerator.domain.cleanup;

import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.illustrations.entity.GeneratedIllustration;
import com.gosu.iconpackgenerator.domain.illustrations.repository.GeneratedIllustrationRepository;
import com.gosu.iconpackgenerator.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrialAssetCleanupService {

    private static final int RETENTION_DAYS = 30;
    private static final String CLEANUP_CRON = "0 0 3 * * *";

    private final GeneratedIconRepository generatedIconRepository;
    private final GeneratedIllustrationRepository generatedIllustrationRepository;
    private final FileStorageService fileStorageService;

    @Scheduled(cron = CLEANUP_CRON)
    @Transactional
    public void purgeExpiredTrialAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deletedIcons = purgeTrialIcons(cutoff);
        int deletedIllustrations = purgeTrialIllustrations(cutoff);

        if (deletedIcons > 0 || deletedIllustrations > 0) {
            log.info("Purged expired trial assets: {} icons, {} illustrations (cutoff={})",
                    deletedIcons, deletedIllustrations, cutoff);
        } else {
            log.debug("No expired trial assets to purge (cutoff={})", cutoff);
        }
    }

    private int purgeTrialIcons(LocalDateTime cutoff) {
        List<GeneratedIcon> watermarkedIcons =
                generatedIconRepository.findByIsWatermarkedTrueAndCreatedAtBefore(cutoff);
        if (watermarkedIcons.isEmpty()) {
            return 0;
        }

        Map<String, List<String>> iconIdsByRequest = new HashMap<>();
        Map<Long, GeneratedIcon> deleteCandidates = new LinkedHashMap<>();

        for (GeneratedIcon icon : watermarkedIcons) {
            deleteCandidates.put(icon.getId(), icon);
            iconIdsByRequest
                    .computeIfAbsent(icon.getRequestId(), key -> new ArrayList<>())
                    .add(icon.getIconId());
        }

        for (Map.Entry<String, List<String>> entry : iconIdsByRequest.entrySet()) {
            List<GeneratedIcon> originals = generatedIconRepository
                    .findByRequestIdAndIconIdInAndIsWatermarkedFalse(entry.getKey(), entry.getValue());
            for (GeneratedIcon original : originals) {
                deleteCandidates.putIfAbsent(original.getId(), original);
            }
        }

        deleteCandidates.values().forEach(icon -> {
            try {
                fileStorageService.deleteIconByRelativePath(icon.getFilePath());
            } catch (Exception e) {
                log.warn("Failed to delete trial icon file {}", icon.getFilePath(), e);
            }
        });
        logDeletionByUserAndRequest(deleteCandidates.values(), "icons");
        generatedIconRepository.deleteAll(deleteCandidates.values());

        return deleteCandidates.size();
    }

    private int purgeTrialIllustrations(LocalDateTime cutoff) {
        List<GeneratedIllustration> watermarkedIllustrations =
                generatedIllustrationRepository.findByIsWatermarkedTrueAndCreatedAtBefore(cutoff);
        if (watermarkedIllustrations.isEmpty()) {
            return 0;
        }

        Map<String, List<String>> illustrationIdsByRequest = new HashMap<>();
        Map<Long, GeneratedIllustration> deleteCandidates = new LinkedHashMap<>();

        for (GeneratedIllustration illustration : watermarkedIllustrations) {
            deleteCandidates.put(illustration.getId(), illustration);
            illustrationIdsByRequest
                    .computeIfAbsent(illustration.getRequestId(), key -> new ArrayList<>())
                    .add(illustration.getIllustrationId());
        }

        for (Map.Entry<String, List<String>> entry : illustrationIdsByRequest.entrySet()) {
            List<GeneratedIllustration> originals = generatedIllustrationRepository
                    .findByRequestIdAndIllustrationIdInAndIsWatermarkedFalse(entry.getKey(), entry.getValue());
            for (GeneratedIllustration original : originals) {
                deleteCandidates.putIfAbsent(original.getId(), original);
            }
        }

        deleteCandidates.values().forEach(illustration -> {
            try {
                fileStorageService.deleteIllustrationByRelativePath(illustration.getFilePath());
            } catch (Exception e) {
                log.warn("Failed to delete trial illustration file {}", illustration.getFilePath(), e);
            }
        });
        logDeletionByUserAndRequest(deleteCandidates.values(), "illustrations");
        generatedIllustrationRepository.deleteAll(deleteCandidates.values());

        return deleteCandidates.size();
    }

    private void logDeletionByUserAndRequest(Iterable<?> assets, String assetLabel) {
        Map<String, Integer> summary = new HashMap<>();
        for (Object asset : assets) {
            String requestId;
            String userEmail;
            if (asset instanceof GeneratedIcon icon) {
                requestId = icon.getRequestId();
                userEmail = icon.getUser() != null ? icon.getUser().getEmail() : "unknown";
            } else if (asset instanceof GeneratedIllustration illustration) {
                requestId = illustration.getRequestId();
                userEmail = illustration.getUser() != null ? illustration.getUser().getEmail() : "unknown";
            } else {
                continue;
            }
            String key = userEmail + "|" + requestId;
            summary.merge(key, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : summary.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String userEmail = parts.length > 0 ? parts[0] : "unknown";
            String requestId = parts.length > 1 ? parts[1] : "unknown";
            log.info("Deleting {} trial {} for user {} (requestId={})",
                    entry.getValue(), assetLabel, userEmail, requestId);
        }
    }
}
