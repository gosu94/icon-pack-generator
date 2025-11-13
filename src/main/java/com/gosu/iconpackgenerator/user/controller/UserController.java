package com.gosu.iconpackgenerator.user.controller;

import com.gosu.iconpackgenerator.admin.service.AdminService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconDto;
import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final GeneratedIconRepository generatedIconRepository;
    private final UserRepository userRepository;
    private final AdminService adminService;
    private final UserService userService;

    @GetMapping("/auth/check")
    public ResponseEntity<Map<String, Object>> checkAuthenticationStatus(@AuthenticationPrincipal OAuth2User principal) {
        Map<String, Object> response = new HashMap<>();

        if (principal instanceof CustomOAuth2User customUser) {
            Long userId = customUser.getUserId();
            User user = userRepository.findById(userId).orElse(customUser.getUser());

            response.put("authenticated", true);
            response.put("user", Map.of(
                    "email", customUser.getEmail(),
                    "id", customUser.getUserId(),
                    "coins", user.getCoins() != null ? user.getCoins() : 0, // Use fresh data from DB
                    "trialCoins", user.getTrialCoins() != null ? user.getTrialCoins() : 0, // Include trial coins
                    "authProvider", user.getAuthProvider() != null ? user.getAuthProvider() : "",
                    "isAdmin", adminService.isAdmin(user) // Include admin status
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
                        icon.getIconId(),
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

    @GetMapping("/user/trial-coins")
    public ResponseEntity<Integer> getUserTrialCoins(@AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(0);
        }

        User user = customUser.getUser();
        return ResponseEntity.ok(user.getTrialCoins() != null ? user.getTrialCoins() : 0);
    }

    @PostMapping("/user/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(@AuthenticationPrincipal OAuth2User principal) {
        Map<String, Object> response = new HashMap<>();
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        Long userId = customUser.getUserId();
        boolean success = userService.updateNotifications(userId, false);
        
        if (success) {
            response.put("success", true);
            response.put("message", "Successfully unsubscribed from notifications");
            log.info("User {} unsubscribed from notifications", userId);
        } else {
            response.put("success", false);
            response.put("message", "Failed to update notification preferences");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Public endpoint to unsubscribe from notifications using a token
     * This endpoint does not require authentication
     */
    @GetMapping("/user/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribeByToken(@RequestParam String token) {
        Map<String, Object> response = new HashMap<>();
        
        boolean success = userService.unsubscribeByToken(token);
        
        if (success) {
            response.put("success", true);
            response.put("message", "Successfully unsubscribed from notifications");
            log.info("User unsubscribed via token");
        } else {
            response.put("success", false);
            response.put("message", "Invalid or expired unsubscribe token");
        }
        
        return ResponseEntity.ok(response);
    }
}
