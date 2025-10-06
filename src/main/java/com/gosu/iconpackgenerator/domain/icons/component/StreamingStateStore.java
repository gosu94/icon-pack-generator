package com.gosu.iconpackgenerator.domain.icons.component;

import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.ApplicationScope;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ApplicationScope
public class StreamingStateStore {

    private final Map<String, Object> streamingRequests = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();
    private final Map<String, Object> generationResults = new ConcurrentHashMap<>();

    // Methods for streamingRequests
    public void addRequest(String requestId, Object request) {
        streamingRequests.put(requestId, request);
    }

    @SuppressWarnings("unchecked")
    public <T> T getRequest(String requestId) {
        return (T) streamingRequests.get(requestId);
    }
    
    // Legacy method for backward compatibility
    public IconGenerationRequest getIconRequest(String requestId) {
        return (IconGenerationRequest) streamingRequests.get(requestId);
    }

    public void removeRequest(String requestId) {
        streamingRequests.remove(requestId);
    }

    // Methods for activeEmitters
    public void addEmitter(String requestId, SseEmitter emitter) {
        activeEmitters.put(requestId, emitter);
    }

    public SseEmitter getEmitter(String requestId) {
        return activeEmitters.get(requestId);
    }

    public void removeEmitter(String requestId) {
        activeEmitters.remove(requestId);
    }

    // Methods for generationResults
    public void addResponse(String requestId, Object response) {
        generationResults.put(requestId, response);
    }

    @SuppressWarnings("unchecked")
    public <T> T getResponse(String requestId) {
        return (T) generationResults.get(requestId);
    }
    
    // Legacy method for backward compatibility
    public IconGenerationResponse getIconResponse(String requestId) {
        return (IconGenerationResponse) generationResults.get(requestId);
    }

    public void removeResponse(String requestId) {
        generationResults.remove(requestId);
    }
}
