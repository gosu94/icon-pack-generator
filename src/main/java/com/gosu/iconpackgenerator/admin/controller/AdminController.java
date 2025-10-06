package com.gosu.iconpackgenerator.admin.controller;

import com.gosu.iconpackgenerator.admin.dto.UserAdminDto;
import com.gosu.iconpackgenerator.admin.service.AdminService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconDto;
import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final GeneratedIconRepository generatedIconRepository;

    /**
     * Check if current user is admin
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkAdminStatus(@AuthenticationPrincipal OAuth2User principal) {
        Map<String, Boolean> response = new HashMap<>();
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            response.put("isAdmin", false);
            return ResponseEntity.ok(response);
        }

        User user = customUser.getUser();
        boolean isAdmin = adminService.isAdmin(user);
        response.put("isAdmin", isAdmin);
        
        log.info("Admin check for user {}: {}", user.getEmail(), isAdmin);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all users (admin only)
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User user = customUser.getUser();
        if (!adminService.isAdmin(user)) {
            log.warn("Non-admin user {} attempted to access admin endpoint", user.getEmail());
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden - Admin access required"));
        }

        List<User> users = userRepository.findAll();
        List<UserAdminDto> userDtos = users.stream()
                .map(u -> {
                    Long iconCount = generatedIconRepository.countByUser(u);
                    return new UserAdminDto(
                            u.getId(),
                            u.getEmail(),
                            u.getLastLogin(),
                            u.getCoins() != null ? u.getCoins() : 0,
                            u.getTrialCoins() != null ? u.getTrialCoins() : 0,
                            iconCount,
                            u.getRegisteredAt(),
                            u.getAuthProvider(),
                            u.getIsActive()
                    );
                })
                .collect(Collectors.toList());

        log.info("Admin user {} retrieved list of {} users", user.getEmail(), userDtos.size());
        return ResponseEntity.ok(userDtos);
    }

    /**
     * Get icons for a specific user (admin only)
     */
    @GetMapping("/users/{userId}/icons")
    public ResponseEntity<?> getUserIcons(@PathVariable Long userId, @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User adminUser = customUser.getUser();
        if (!adminService.isAdmin(adminUser)) {
            log.warn("Non-admin user {} attempted to access admin endpoint", adminUser.getEmail());
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden - Admin access required"));
        }

        User targetUser = userRepository.findById(userId)
                .orElse(null);
        
        if (targetUser == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        List<GeneratedIcon> icons = generatedIconRepository.findByUserOrderByCreatedAtDesc(targetUser);
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

        log.info("Admin user {} retrieved {} icons for user {}", adminUser.getEmail(), iconDtos.size(), targetUser.getEmail());
        return ResponseEntity.ok(iconDtos);
    }

    /**
     * Update coins for a specific user (admin only)
     */
    @PostMapping("/users/{userId}/coins")
    public ResponseEntity<?> updateUserCoins(@PathVariable Long userId, @RequestBody Map<String, Integer> payload, @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User adminUser = customUser.getUser();
        if (!adminService.isAdmin(adminUser)) {
            log.warn("Non-admin user {} attempted to update coins", adminUser.getEmail());
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden - Admin access required"));
        }

        User targetUser = userRepository.findById(userId)
                .orElse(null);

        if (targetUser == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Integer coins = payload.get("coins");
        Integer trialCoins = payload.get("trialCoins");

        if (coins != null) {
            targetUser.setCoins(coins);
        }
        if (trialCoins != null) {
            targetUser.setTrialCoins(trialCoins);
        }

        userRepository.save(targetUser);

        log.info("Admin user {} updated coins for user {}. New values: coins={}, trialCoins={}", adminUser.getEmail(), targetUser.getEmail(), targetUser.getCoins(), targetUser.getTrialCoins());
        return ResponseEntity.ok(Map.of("message", "Coins updated successfully"));
    }
}
