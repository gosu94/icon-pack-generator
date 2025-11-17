package com.gosu.iconpackgenerator.domain.status;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class GenerationStatusService {

    private final Map<String, GenerationInProgress> activeGenerations = new ConcurrentHashMap<>();

    public String markGenerationStart(String type) {
        return markGenerationStart(type, null);
    }

    public String markGenerationStart(String type, String requestId) {
        String id = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
        GenerationInProgress generation = new GenerationInProgress(id, type, Instant.now());
        activeGenerations.put(id, generation);
        log.debug("Marked generation {} ({}) as in-progress", id, type);
        return id;
    }

    public void markGenerationComplete(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        GenerationInProgress removed = activeGenerations.remove(requestId);
        if (removed != null) {
            log.debug("Marked generation {} ({}) as completed", removed.getRequestId(), removed.getType());
        }
    }

    public GenerationStatusResponse getStatus() {
        List<GenerationInProgress> generations = new ArrayList<>(activeGenerations.values());
        return new GenerationStatusResponse(!generations.isEmpty(), generations.size(), generations);
    }

    @Value
    public static class GenerationInProgress {
        String requestId;
        String type;
        Instant startedAt;
    }

    @Value
    public static class GenerationStatusResponse {
        boolean inProgress;
        int activeCount;
        List<GenerationInProgress> activeGenerations;
    }
}
