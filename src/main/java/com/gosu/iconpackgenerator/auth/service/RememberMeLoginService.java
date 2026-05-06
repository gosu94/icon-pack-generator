package com.gosu.iconpackgenerator.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RememberMeLoginService {

    private static final String REMEMBER_ME_PARAMETER = "remember-me";

    private final PersistentTokenBasedRememberMeServices rememberMeServices;

    public void loginSuccess(HttpServletRequest request,
                             HttpServletResponse response,
                             Authentication authentication) {
        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                if (REMEMBER_ME_PARAMETER.equals(name)) {
                    return "true";
                }
                return super.getParameter(name);
            }

            @Override
            public String[] getParameterValues(String name) {
                if (REMEMBER_ME_PARAMETER.equals(name)) {
                    return new String[]{"true"};
                }
                return super.getParameterValues(name);
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                Map<String, String[]> parameterMap = new HashMap<>(super.getParameterMap());
                parameterMap.put(REMEMBER_ME_PARAMETER, new String[]{"true"});
                return parameterMap;
            }
        };

        rememberMeServices.loginSuccess(wrappedRequest, response, authentication);
    }
}
