package com.gosu.iconpackgenerator.admin.filter;

import com.gosu.iconpackgenerator.admin.service.AdminService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuthorizationFilter extends OncePerRequestFilter {

    private final AdminService adminService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        
        // Check if this is an admin endpoint
        if (requestPath.startsWith("/api/admin/")) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthenticated access attempt to admin endpoint: {}", requestPath);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Authentication required\"}");
                return;
            }
            
            // Check if user is admin
            if (authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
                User user = customUser.getUser();
                if (!adminService.isAdmin(user)) {
                    log.warn("Non-admin user {} attempted to access admin endpoint: {}", 
                            user.getEmail(), requestPath);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden - Admin access required\"}");
                    return;
                }
                log.debug("Admin user {} accessing endpoint: {}", user.getEmail(), requestPath);
            } else {
                log.warn("Invalid principal type attempting to access admin endpoint: {}", requestPath);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Forbidden - Admin access required\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
