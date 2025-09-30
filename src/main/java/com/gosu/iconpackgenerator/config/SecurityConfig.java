package com.gosu.iconpackgenerator.config;

import com.gosu.iconpackgenerator.admin.filter.AdminAuthorizationFilter;
import com.gosu.iconpackgenerator.auth.provider.EmailPasswordAuthenticationProvider;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2UserService;
import com.gosu.iconpackgenerator.user.service.CustomOidcUserService;
import com.gosu.iconpackgenerator.user.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@Profile("!test") // Only apply this security config outside of test profile
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final EmailPasswordAuthenticationProvider emailPasswordAuthenticationProvider;
    private final Environment environment;
    private final DataSource dataSource;
    private final CustomUserDetailsService customUserDetailsService;
    private final AdminAuthorizationFilter adminAuthorizationFilter;

    @Value("${app.security.remember-me.key:defaultRememberMeSecretKey123}")
    private String rememberMeKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(adminAuthorizationFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            .authorizeHttpRequests(authz -> authz
                // Authentication endpoints - public (must be first)
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                // Static resources - public
                .requestMatchers("/", "/error", "/favicon.ico").permitAll()
                .requestMatchers("/static/**", "/_next/**").permitAll()
                .requestMatchers("/webjars/**", "/css/**", "/js/**", "/images/**").permitAll()
                // Public frontend pages - no authentication required
                .requestMatchers("/privacy/**", "/terms/**").permitAll()
                .requestMatchers("/payment/**").permitAll()
                .requestMatchers("/password-setup/**").permitAll()
                // Protected frontend pages - require authentication
                .requestMatchers("/dashboard/**", "/dashboard").authenticated()
                .requestMatchers("/settings/**", "/settings").authenticated()
                .requestMatchers("/gallery/**", "/gallery").authenticated()
                .requestMatchers("/store/**", "/store").authenticated()
                .requestMatchers("/feedback/**", "/feedback").authenticated()
                .requestMatchers("/control-panel/**", "/control-panel").authenticated()
                // Protected API endpoints - require authentication
                .requestMatchers("/api/user/**", "/api/icons/**", "/api/gallery/**").authenticated()
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/generate-stream", "/stream/**", "/generate-more").authenticated()
                .requestMatchers("/export", "/export-gallery").authenticated()
                // All other requests (including home page) - public
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService)
                )
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .loginPage("/login")
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
            )
            // Remember Me configuration - handles AUTOMATIC AUTHENTICATION on subsequent visits
            // Note: Token CREATION is handled manually in AuthController for REST API login
            // This configuration provides the infrastructure for automatic login when users return
            .rememberMe(remember -> remember
                .tokenRepository(persistentTokenRepository())
                .tokenValiditySeconds(30 * 24 * 60 * 60) // 30 days
                .key(rememberMeKey) // Use configurable secret key from environment
                .userDetailsService(customUserDetailsService)
                .rememberMeParameter("remember-me") // HTML form parameter name
                .rememberMeCookieName("remember-me") // Cookie name
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    // For API requests, return 401
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.getWriter().write("{\"error\":\"Authentication required\"}");
                        response.setContentType("application/json");
                    } else {
                        // For frontend routes, redirect to login with return URL
                        String returnUrl = request.getRequestURI();
                        if (request.getQueryString() != null) {
                            returnUrl += "?" + request.getQueryString();
                        }
                        response.sendRedirect("/login?redirect=" + java.net.URLEncoder.encode(returnUrl, "UTF-8"));
                    }
                })
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "remember-me")
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(buildCSPDirectives())
                )
            );

        return http.build();
    }

    private String buildCSPDirectives() {
        // Check if we're in development environment
        boolean isDevelopment = Arrays.asList(environment.getActiveProfiles()).contains("dev") ||
                                Arrays.asList(environment.getActiveProfiles()).contains("local");
        
        log.info("CSP Configuration - Environment profiles: {}, Server port: {}, isDevelopment: {}", 
                Arrays.toString(environment.getActiveProfiles()), 
                environment.getProperty("server.port", "8080"), 
                isDevelopment);
        
        String cspPolicy;
        if (isDevelopment) {
            // Very permissive CSP for development to ensure React/Next.js works
            cspPolicy = "default-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob: " +
                    "localhost:* 127.0.0.1:* ws: wss:; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval' " +
                    "localhost:* 127.0.0.1:* " +
                    "https://js.stripe.com https://m.stripe.network " +
                    "https://accounts.google.com https://apis.google.com; " +
                    "style-src 'self' 'unsafe-inline' " +
                    "localhost:* 127.0.0.1:* " +
                    "https://fonts.googleapis.com https://js.stripe.com; " +
                    "font-src 'self' data: " +
                    "https://fonts.gstatic.com; " +
                    "img-src 'self' data: blob: " +
                    "localhost:* 127.0.0.1:* " +
                    "https: data:; " +
                    "connect-src 'self' " +
                    "localhost:* 127.0.0.1:* ws://localhost:* wss://localhost:* " +
                    "https://api.stripe.com https://m.stripe.network " +
                    "https://accounts.google.com https://oauth2.googleapis.com; " +
                    "frame-src 'self' " +
                    "https://js.stripe.com https://hooks.stripe.com " +
                    "https://accounts.google.com; " +
                    "form-action 'self' " +
                    "https://accounts.google.com https://oauth2.googleapis.com;";
        } else {
            // Production CSP - simplified and more permissive to avoid hash management issues
            // This is more permissive but eliminates the constant need to add new hashes
            cspPolicy = "default-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob: https: " +
                    "https://js.stripe.com https://m.stripe.network " +
                    "https://accounts.google.com https://apis.google.com; " +
                    "img-src 'self' data: blob: https:; " +
                    "object-src 'none';";
        }
        
        log.info("Applied CSP Policy ({}): {}", 
                isDevelopment ? "DEVELOPMENT" : "PRODUCTION", 
                cspPolicy);
        return cspPolicy;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(emailPasswordAuthenticationProvider);
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl tokenRepository = new JdbcTokenRepositoryImpl();
        tokenRepository.setDataSource(dataSource);
        return tokenRepository;
    }

}
