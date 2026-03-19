package com.gosu.iconpackgenerator.config;

import com.gosu.iconpackgenerator.singal.SignalMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturnMethodNotAllowedWithoutSendingSignalForUnsupportedMethod() {
        SignalMessageService signalMessageService = mock(SignalMessageService.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(signalMessageService, true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("POST");

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/sdk");

        ResponseEntity<Void> response = handler.handleMethodNotSupported(request, exception);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        verify(signalMessageService, never()).sendSignalMessage(org.mockito.ArgumentMatchers.anyString());
    }
}
