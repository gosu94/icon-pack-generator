package com.gosu.iconpackgenerator.auth.controller;

import com.gosu.iconpackgenerator.auth.dto.ChangePasswordRequest;
import com.gosu.iconpackgenerator.auth.dto.EmailCheckRequest;
import com.gosu.iconpackgenerator.auth.dto.EmailCheckResponse;
import com.gosu.iconpackgenerator.auth.dto.LoginRequest;
import com.gosu.iconpackgenerator.auth.dto.LoginResponse;
import com.gosu.iconpackgenerator.auth.dto.PasswordSetupRequest;
import com.gosu.iconpackgenerator.auth.dto.SendEmailRequest;
import com.gosu.iconpackgenerator.auth.dto.TokenValidationRequest;
import com.gosu.iconpackgenerator.user.service.EmailAuthService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AuthController {

    private final EmailAuthService emailAuthService;
    private final AuthenticationManager authenticationManager;
    private final PersistentTokenRepository persistentTokenRepository;
    
    @Value("${app.security.remember-me.key:defaultRememberMeSecretKey123}")
    private String rememberMeKey;

    @PostMapping("/check-email")
    public ResponseEntity<EmailCheckResponse> checkEmail(@Valid @RequestBody EmailCheckRequest request) {
        try {
            EmailAuthService.EmailCheckResult result = emailAuthService.checkEmail(request.getEmail());
            
            EmailCheckResponse response = new EmailCheckResponse();
            response.setEmail(request.getEmail());
            
            switch (result) {
                case EMAIL_NOT_FOUND:
                    response.setExists(false);
                    response.setHasPassword(false);
                    break;
                case NO_PASSWORD_SET:
                    response.setExists(true);
                    response.setHasPassword(false);
                    break;
                case PASSWORD_REQUIRED:
                    response.setExists(true);
                    response.setHasPassword(true);
                    break;
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking email: {}", request.getEmail(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            // Create authentication token
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
            
            // Authenticate using the authentication manager (which will use our custom provider)
            Authentication authentication = authenticationManager.authenticate(authToken);
            
            // Set the authentication in the security context
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            
            // Save the security context to the session
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
            
            // Handle remember me functionality if requested
            // Note: This manually creates remember me tokens for REST API login
            // SecurityConfig handles automatic authentication when users return with valid tokens
            if (request.isRememberMe()) {
                log.debug("Creating remember me token for user: {}", request.getEmail());
                createRememberMeToken(request.getEmail(), httpRequest, httpResponse);
            }
            
            // Extract user info from the authenticated principal
            CustomOAuth2User customUser = (CustomOAuth2User) authentication.getPrincipal();
            User user = customUser.getUser();
            
            LoginResponse response = new LoginResponse();
            response.setSuccess(true);
            response.setMessage("Login successful");
            response.setUserId(user.getId());
            response.setEmail(user.getEmail());
            response.setCoins(user.getCoins());
            response.setTrialCoins(user.getTrialCoins());
            
            log.info("User logged in successfully: {}", user.getEmail());
            return ResponseEntity.ok(response);
            
        } catch (BadCredentialsException e) {
            log.warn("Invalid credentials for email: {}", request.getEmail());
            LoginResponse response = new LoginResponse();
            response.setSuccess(false);
            response.setMessage("Invalid email or password");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error during login for email: {}", request.getEmail(), e);
            LoginResponse response = new LoginResponse();
            response.setSuccess(false);
            response.setMessage("An error occurred during login");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/send-setup-email")
    public ResponseEntity<Void> sendPasswordSetupEmail(@Valid @RequestBody SendEmailRequest request) {
        try {
            boolean sent = emailAuthService.sendPasswordSetupEmail(request.getEmail());
            
            if (sent) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
            
        } catch (Exception e) {
            log.error("Error sending password setup email: {}", request.getEmail(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/send-reset-email")
    public ResponseEntity<Void> sendPasswordResetEmail(@Valid @RequestBody SendEmailRequest request) {
        try {
            // Always return success for security (don't reveal if email exists)
            emailAuthService.sendPasswordResetEmail(request.getEmail());
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("Error sending password reset email: {}", request.getEmail(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/validate-token")
    public ResponseEntity<Void> validateToken(@Valid @RequestBody TokenValidationRequest request) {
        try {
            boolean isValid = emailAuthService.validateToken(request.getToken(), request.isReset());
            
            if (isValid) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
            
        } catch (Exception e) {
            log.error("Error validating token: {}", request.getToken(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/set-password")
    public ResponseEntity<LoginResponse> setPassword(@Valid @RequestBody PasswordSetupRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            User user = emailAuthService.setPassword(
                request.getToken(),
                request.getPassword(),
                request.isReset()
            );

            if (user != null) {
                // Automatically log the user in
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword());

                Authentication authentication = authenticationManager.authenticate(authToken);

                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(securityContext);

                HttpSession session = httpRequest.getSession(true);
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

                CustomOAuth2User customUser = (CustomOAuth2User) authentication.getPrincipal();
                User authenticatedUser = customUser.getUser();

                LoginResponse response = new LoginResponse();
                response.setSuccess(true);
                response.setMessage("Password set and user logged in successfully");
                response.setUserId(authenticatedUser.getId());
                response.setEmail(authenticatedUser.getEmail());
                response.setCoins(authenticatedUser.getCoins());
                response.setTrialCoins(authenticatedUser.getTrialCoins());

                log.info("User password set and logged in successfully: {}", authenticatedUser.getEmail());
                return ResponseEntity.ok(response);
            } else {
                LoginResponse response = new LoginResponse();
                response.setSuccess(false);
                response.setMessage("Failed to set password. Please try again or request a new link.");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (BadCredentialsException e) {
            log.warn("Automatic login failed after password set for token: {}", request.getToken());
            LoginResponse response = new LoginResponse();
            response.setSuccess(false);
            response.setMessage("An error occurred during automatic login.");
            return ResponseEntity.status(401).body(response);
        } catch (Exception e) {
            log.error("Error setting password for token: {}", request.getToken(), e);
            LoginResponse response = new LoginResponse();
            response.setSuccess(false);
            response.setMessage("An error occurred. Please try again.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).build();
        }

        try {
            // Validate password confirmation
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().build();
            }

            Long userId = customUser.getUserId();
            boolean success = emailAuthService.changePassword(
                userId,
                request.getCurrentPassword(),
                request.getNewPassword()
            );

            if (success) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.badRequest().build();
            }

        } catch (Exception e) {
            log.error("Error changing password for user: {}", customUser.getUserId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manually create a remember me token and cookie
     */
    private void createRememberMeToken(String username, HttpServletRequest request, HttpServletResponse response) {
        try {
            // Generate secure random values for series and token
            SecureRandom random = new SecureRandom();
            byte[] seriesBytes = new byte[16];
            byte[] tokenBytes = new byte[16];
            
            random.nextBytes(seriesBytes);
            random.nextBytes(tokenBytes);
            
            String series = Base64.getEncoder().encodeToString(seriesBytes);
            String tokenValue = Base64.getEncoder().encodeToString(tokenBytes);
            
            // Create persistent token
            PersistentRememberMeToken persistentToken = new PersistentRememberMeToken(
                    username, 
                    series, 
                    tokenValue, 
                    new Date()
            );
            
            // Save to database
            persistentTokenRepository.createNewToken(persistentToken);
            
            // Create remember me cookie value: series:token
            String cookieValue = series + ":" + tokenValue;
            
            // Create cookie
            Cookie rememberMeCookie = new Cookie("remember-me", cookieValue);
            rememberMeCookie.setMaxAge(30 * 24 * 60 * 60); // 30 days
            rememberMeCookie.setPath("/");
            rememberMeCookie.setHttpOnly(true);
            rememberMeCookie.setSecure(request.isSecure()); // Use secure flag if HTTPS
            
            response.addCookie(rememberMeCookie);
            
            log.info("Remember me token created successfully for user: {}", username);
            
        } catch (Exception e) {
            log.error("Error creating remember me token for user: {}", username, e);
        }
    }
}
