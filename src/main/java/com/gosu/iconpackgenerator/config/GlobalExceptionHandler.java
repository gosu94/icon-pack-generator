package com.gosu.iconpackgenerator.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.MultipartStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MultipartException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Void> handleMultipartException(MultipartException exception) {
        if (isClientDisconnected(exception)) {
            log.info("Multipart request ended unexpectedly: {}", getRootMessage(exception));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        log.error("Multipart request failed", exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(AsyncRequestNotUsableException exception) {
        if (isClientDisconnected(exception)) {
            log.info("Async request disconnected: {}", getRootMessage(exception));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        log.error("Async request failed", exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    private boolean isClientDisconnected(MultipartException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof MultipartStream.MalformedStreamException) {
                return true;
            }
            if (cause.getMessage() != null && cause.getMessage().contains("Stream ended unexpectedly")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isClientDisconnected(AsyncRequestNotUsableException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof java.io.IOException) {
                String message = cause.getMessage();
                if (message != null && (message.contains("Broken pipe")
                        || message.contains("Connection reset")
                        || message.contains("Socket closed"))) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String getRootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : throwable.getMessage();
    }
}
