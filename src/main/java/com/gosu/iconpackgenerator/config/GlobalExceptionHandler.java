package com.gosu.iconpackgenerator.config;

import com.gosu.iconpackgenerator.singal.SignalMessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.MultipartStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final SignalMessageService signalMessageService;
    private final boolean exceptionSignalEnabled;

    public GlobalExceptionHandler(
            SignalMessageService signalMessageService,
            @Value("${app.exception-alerting.signal.enabled:false}") boolean exceptionSignalEnabled
    ) {
        this.signalMessageService = signalMessageService;
        this.exceptionSignalEnabled = exceptionSignalEnabled;
    }

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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(
            HttpServletRequest request,
            NoResourceFoundException exception
    ) {
        if (shouldSuppressMissingResource(request)) {
            log.debug("Ignoring missing resource scan for {}", request.getRequestURI());
        } else {
            log.info("Missing static resource for {} {}: {}", request.getMethod(), request.getRequestURI(), getRootMessage(exception));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(
            HttpServletRequest request,
            HttpRequestMethodNotSupportedException exception
    ) {
        if (shouldSuppressUnsupportedMethodRequest(request)) {
            log.debug("Ignoring unsupported method scan for {} {}", request.getMethod(), request.getRequestURI());
        } else {
            log.info("Unsupported request method for {} {}: {}", request.getMethod(), request.getRequestURI(), getRootMessage(exception));
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Void> handleUnexpectedException(HttpServletRequest request, Exception exception) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
        sendExceptionSignal(request, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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

    private void sendExceptionSignal(HttpServletRequest request, Exception exception) {
        if (!exceptionSignalEnabled) {
            return;
        }

        String message = String.format(
                "[IconPackGen]: Exception thrown - %s: %s",
                exception.getClass().getName(),
                getRootMessage(exception)
        );
        signalMessageService.sendSignalMessage(message);
    }

    private boolean shouldSuppressMissingResource(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null) {
            return false;
        }

        return "/robots.txt".equals(requestUri)
                || "/favicon.ico".equals(requestUri)
                || "/sdk".equals(requestUri)
                || requestUri.startsWith("/.env")
                || requestUri.startsWith("/wp-")
                || requestUri.startsWith("/wordpress")
                || requestUri.startsWith("/boaform")
                || requestUri.startsWith("/cgi-bin")
                || requestUri.startsWith("/vendor/")
                || requestUri.startsWith("/php")
                || requestUri.endsWith(".php");
    }

    private boolean shouldSuppressUnsupportedMethodRequest(HttpServletRequest request) {
        return shouldSuppressMissingResource(request);
    }
}
