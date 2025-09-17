package com.gosu.iconpackgenerator.auth.controller;

import com.gosu.iconpackgenerator.auth.dto.EmailCheckRequest;
import com.gosu.iconpackgenerator.auth.dto.EmailCheckResponse;
import com.gosu.iconpackgenerator.auth.dto.LoginRequest;
import com.gosu.iconpackgenerator.auth.dto.LoginResponse;
import com.gosu.iconpackgenerator.auth.dto.PasswordSetupRequest;
import com.gosu.iconpackgenerator.auth.dto.SendEmailRequest;
import com.gosu.iconpackgenerator.auth.dto.TokenValidationRequest;
import com.gosu.iconpackgenerator.auth.service.EmailAuthService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AuthController {

    private final EmailAuthService emailAuthService;
    private final AuthenticationManager authenticationManager;

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
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
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
    public ResponseEntity<Void> setPassword(@Valid @RequestBody PasswordSetupRequest request) {
        try {
            boolean success = emailAuthService.setPassword(
                request.getToken(), 
                request.getPassword(), 
                request.isReset()
            );
            
            if (success) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
            
        } catch (Exception e) {
            log.error("Error setting password for token: {}", request.getToken(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
