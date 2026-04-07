package com.gosu.iconpackgenerator.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

@Service
public class OAuthRememberMeCookieService {

    public static final String OAUTH_REMEMBER_ME_COOKIE = "oauth-remember-me";
    public static final String REMEMBER_ME_COOKIE = "remember-me";

    public boolean isRememberMeRequested(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }

        for (Cookie cookie : cookies) {
            if (OAUTH_REMEMBER_ME_COOKIE.equals(cookie.getName())) {
                return "true".equalsIgnoreCase(cookie.getValue());
            }
        }

        return false;
    }

    public void clearOAuthRememberMeCookie(HttpServletRequest request, HttpServletResponse response) {
        clearCookie(response, OAUTH_REMEMBER_ME_COOKIE, request.isSecure());
    }

    public void clearPersistentRememberMeCookie(HttpServletRequest request, HttpServletResponse response) {
        clearCookie(response, REMEMBER_ME_COOKIE, request.isSecure());
    }

    private void clearCookie(HttpServletResponse response, String name, boolean secure) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        response.addCookie(cookie);
    }
}
