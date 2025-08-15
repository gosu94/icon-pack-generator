package com.gosu.icon_pack_generator.exception;

/**
 * Custom exception for fal.ai API related errors
 */
public class FalAiException extends RuntimeException {
    
    public FalAiException(String message) {
        super(message);
    }
    
    public FalAiException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public FalAiException(Throwable cause) {
        super(cause);
    }
}
