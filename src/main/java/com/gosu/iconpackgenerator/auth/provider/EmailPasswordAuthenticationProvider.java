package com.gosu.iconpackgenerator.auth.provider;

import com.gosu.iconpackgenerator.auth.service.EmailAuthService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailPasswordAuthenticationProvider implements AuthenticationProvider {

    private final EmailAuthService emailAuthService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = authentication.getCredentials().toString();

        log.debug("Attempting to authenticate user with email: {}", email);

        Optional<User> userOpt = emailAuthService.authenticateUser(email, password);
        
        if (userOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userOpt.get();
        
        // Create OAuth2User-compatible attributes for consistency with Google OAuth
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", user.getEmail());
        attributes.put("name", user.getEmail()); // Use email as name for email auth users
        
        // Create CustomOAuth2User for consistency with the rest of the system
        CustomOAuth2User customUser = new CustomOAuth2User(attributes, user);

        // Create successful authentication token with ROLE_USER authority
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                customUser,
                null, // credentials are cleared after successful authentication
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        
        log.info("Successfully authenticated user: {}", email);
        return authToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
