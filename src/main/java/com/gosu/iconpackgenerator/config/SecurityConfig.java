package com.gosu.iconpackgenerator.config;

import com.gosu.iconpackgenerator.auth.provider.EmailPasswordAuthenticationProvider;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2UserService;
import com.gosu.iconpackgenerator.user.service.CustomOidcUserService;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@Profile("!test") // Only apply this security config outside of test profile
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final EmailPasswordAuthenticationProvider emailPasswordAuthenticationProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
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
                // Protected API endpoints - require authentication
                .requestMatchers("/api/user/**", "/api/icons/**", "/api/gallery/**").authenticated()
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
                .deleteCookies("JSESSIONID")
            );

        return http.build();
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
}
