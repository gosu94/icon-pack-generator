package com.gosu.iconpackgenerator.user.controller;

import com.gosu.iconpackgenerator.domain.dto.IconDto;
import com.gosu.iconpackgenerator.domain.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import com.gosu.iconpackgenerator.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final GeneratedIconRepository generatedIconRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @GetMapping("/auth/check")
    public ResponseEntity<Map<String, Object>> checkAuthenticationStatus(@AuthenticationPrincipal OAuth2User principal) {
        Map<String, Object> response = new HashMap<>();
        
        if (principal instanceof CustomOAuth2User customUser) {
            // Get fresh user data from database to ensure up-to-date coin count
            Long userId = customUser.getUserId();
            User freshUser = userRepository.findById(userId).orElse(customUser.getUser());
            
            response.put("authenticated", true);
            response.put("user", Map.of(
                "email", customUser.getEmail(),
                "id", customUser.getUserId(),
                "coins", freshUser.getCoins() // Use fresh data from DB
            ));
        } else {
            response.put("authenticated", false);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, String>> logout() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/icons")
    public ResponseEntity<List<IconDto>> getUserIcons(@AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(Collections.emptyList());
        }

        User user = customUser.getUser();
        List<GeneratedIcon> icons = generatedIconRepository.findByUserOrderByCreatedAtDesc(user);

        List<IconDto> iconDtos = icons.stream()
                .map(icon -> new IconDto(
                        icon.getFilePath(),
                        icon.getDescription(),
                        icon.getServiceSource(),
                        icon.getRequestId(),
                        icon.getIconType(),
                        icon.getTheme()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(iconDtos);
    }
    
    @GetMapping("/user/coins")
    public ResponseEntity<Integer> getUserCoins(@AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(0);
        }

        User user = customUser.getUser();
        return ResponseEntity.ok(user.getCoins());
    }
}
