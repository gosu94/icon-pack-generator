package com.gosu.iconpackgenerator.auth.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2RememberMeSuccessHandler implements AuthenticationSuccessHandler {

    private final RememberMeLoginService rememberMeLoginService;
    private final OAuthRememberMeCookieService oAuthRememberMeCookieService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (oAuthRememberMeCookieService.isRememberMeRequested(request)) {
            rememberMeLoginService.loginSuccess(request, response, authentication);
        } else {
            oAuthRememberMeCookieService.clearPersistentRememberMeCookie(request, response);
        }

        oAuthRememberMeCookieService.clearOAuthRememberMeCookie(request, response);
        response.sendRedirect("/dashboard");
    }
}
