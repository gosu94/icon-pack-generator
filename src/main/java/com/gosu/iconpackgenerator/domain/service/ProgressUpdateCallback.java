package com.gosu.iconpackgenerator.domain.service;

import com.gosu.iconpackgenerator.domain.dto.ServiceProgressUpdate;

/**
 * Functional interface for receiving real-time progress updates during icon generation
 */
@FunctionalInterface
public interface ProgressUpdateCallback {
    
    /**
     * Called when a service update occurs
     * @param update The progress update containing service status and results
     */
    void onUpdate(ServiceProgressUpdate update);
}
