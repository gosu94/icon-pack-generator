package com.gosu.iconpackgenerator.admin.controller;

import com.gosu.iconpackgenerator.admin.dto.ActivityStatsResponse;
import com.gosu.iconpackgenerator.admin.dto.DailyCountDto;
import com.gosu.iconpackgenerator.admin.dto.PagedResponse;
import com.gosu.iconpackgenerator.admin.dto.UserAdminDto;
import com.gosu.iconpackgenerator.admin.service.AdminService;
import com.gosu.iconpackgenerator.domain.icons.dto.IconDto;
import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationDto;
import com.gosu.iconpackgenerator.domain.illustrations.entity.GeneratedIllustration;
import com.gosu.iconpackgenerator.domain.illustrations.repository.GeneratedIllustrationRepository;
import com.gosu.iconpackgenerator.domain.labels.repository.GeneratedLabelRepository;
import com.gosu.iconpackgenerator.domain.labels.dto.LabelDto;
import com.gosu.iconpackgenerator.domain.labels.entity.GeneratedLabel;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupDto;
import com.gosu.iconpackgenerator.domain.mockups.entity.GeneratedMockup;
import com.gosu.iconpackgenerator.domain.mockups.repository.GeneratedMockupRepository;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
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
    private final GeneratedIllustrationRepository generatedIllustrationRepository;
    private final GeneratedMockupRepository generatedMockupRepository;
    private final GeneratedLabelRepository generatedLabelRepository;

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
     * Get all users with pagination and sorting (admin only)
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User user = customUser.getUser();
        if (!adminService.isAdmin(user)) {
            log.warn("Non-admin user {} attempted to access admin endpoint", user.getEmail());
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden - Admin access required"));
        }

        // Create sort object
        Sort sort = direction.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        
        // Create pageable object
        Pageable pageable = PageRequest.of(page, size, sort);
        
        String trimmedSearch = search != null ? search.trim() : "";

        // Fetch paginated users
        Page<User> userPage = StringUtils.hasText(trimmedSearch)
                ? userRepository.searchUsers(trimmedSearch, pageable)
                : userRepository.findAll(pageable);
        
        // Map to DTOs
        List<UserAdminDto> userDtos = userPage.getContent().stream()
                .map(u -> {
                    int iconCount = filterWatermarkedIcons(generatedIconRepository.findByUser(u)).size();
                    int illustrationCount = filterWatermarkedIllustrations(
                            generatedIllustrationRepository.findByUserOrderByCreatedAtDesc(u)).size();
                    long mockupCount = generatedMockupRepository.countByUser(u);
                    Long labelCount = generatedLabelRepository.countByUser(u);
                    return new UserAdminDto(
                            u.getId(),
                            u.getEmail(),
                            u.getLastLogin(),
                            u.getCoins() != null ? u.getCoins() : 0,
                            u.getTrialCoins() != null ? u.getTrialCoins() : 0,
                            (long) iconCount,
                            (long) illustrationCount,
                            mockupCount,
                            labelCount,
                            u.getRegisteredAt(),
                            u.getAuthProvider(),
                            u.getIsActive()
                    );
                })
                .collect(Collectors.toList());

        // Create paginated response
        PagedResponse<UserAdminDto> response = new PagedResponse<>(
                userDtos,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isFirst(),
                userPage.isLast()
        );

        log.info("Admin user {} retrieved page {} of users (total: {}, search: {})",
                user.getEmail(), page, userPage.getTotalElements(),
                StringUtils.hasText(trimmedSearch) ? trimmedSearch : "<none>");
        return ResponseEntity.ok(response);
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
        List<GeneratedIcon> filteredIcons = filterWatermarkedIcons(icons);
        List<IconDto> iconDtos = filteredIcons.stream()
                .map(icon -> new IconDto(
                        icon.getFilePath(),
                        icon.getIconId(),
                        icon.getDescription(),
                        icon.getServiceSource(),
                        icon.getRequestId(),
                        icon.getIconType(),
                        icon.getTheme(),
                        Boolean.TRUE.equals(icon.getIsWatermarked())
                ))
                .collect(Collectors.toList());

        log.info("Admin user {} retrieved {} icons for user {}", adminUser.getEmail(), iconDtos.size(), targetUser.getEmail());
        return ResponseEntity.ok(iconDtos);
    }

    /**
     * Get illustrations for a specific user (admin only)
     */
    @GetMapping("/users/{userId}/illustrations")
    public ResponseEntity<?> getUserIllustrations(@PathVariable Long userId, @AuthenticationPrincipal OAuth2User principal) {
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

        List<GeneratedIllustration> illustrations = generatedIllustrationRepository.findByUserOrderByCreatedAtDesc(targetUser);
        List<GeneratedIllustration> filteredIllustrations = filterWatermarkedIllustrations(illustrations);
        List<IllustrationDto> illustrationDtos = filteredIllustrations.stream()
                .map(illustration -> new IllustrationDto(
                        illustration.getFilePath(),
                        illustration.getDescription(),
                        illustration.getIllustrationType(),
                        illustration.getRequestId()
                ))
                .collect(Collectors.toList());

        log.info("Admin user {} retrieved {} illustrations for user {}", adminUser.getEmail(), illustrationDtos.size(), targetUser.getEmail());
        return ResponseEntity.ok(illustrationDtos);
    }

    private List<GeneratedIcon> filterWatermarkedIcons(List<GeneratedIcon> icons) {
        Map<String, Boolean> hasWatermarkByGroup = new HashMap<>();
        for (GeneratedIcon icon : icons) {
            if (Boolean.TRUE.equals(icon.getIsWatermarked())) {
                hasWatermarkByGroup.put(buildWatermarkGroupKey(icon), true);
            }
        }

        List<GeneratedIcon> filtered = new ArrayList<>();
        for (GeneratedIcon icon : icons) {
            if (isPrivateIconPath(icon.getFilePath())) {
                continue;
            }
            if (Boolean.TRUE.equals(icon.getIsWatermarked())
                    || !hasWatermarkByGroup.getOrDefault(buildWatermarkGroupKey(icon), false)) {
                filtered.add(icon);
            }
        }
        return filtered;
    }

    private String buildWatermarkGroupKey(GeneratedIcon icon) {
        String generationIndex = icon.getGenerationIndex() != null ? icon.getGenerationIndex().toString() : "1";
        String iconType = icon.getIconType() != null ? icon.getIconType() : "unknown";
        return icon.getRequestId() + "|" + iconType + "|" + generationIndex;
    }

    private boolean isPrivateIconPath(String filePath) {
        return filePath != null && filePath.startsWith("/private-icons/");
    }

    private List<GeneratedIllustration> filterWatermarkedIllustrations(List<GeneratedIllustration> illustrations) {
        Map<String, Boolean> hasWatermarkByGroup = new HashMap<>();
        for (GeneratedIllustration illustration : illustrations) {
            if (Boolean.TRUE.equals(illustration.getIsWatermarked())) {
                hasWatermarkByGroup.put(buildIllustrationWatermarkGroupKey(illustration), true);
            }
        }

        List<GeneratedIllustration> filtered = new ArrayList<>();
        for (GeneratedIllustration illustration : illustrations) {
            if (isPrivateIllustrationPath(illustration.getFilePath())) {
                continue;
            }
            if (Boolean.TRUE.equals(illustration.getIsWatermarked())
                    || !hasWatermarkByGroup.getOrDefault(buildIllustrationWatermarkGroupKey(illustration), false)) {
                filtered.add(illustration);
            }
        }
        return filtered;
    }

    private String buildIllustrationWatermarkGroupKey(GeneratedIllustration illustration) {
        String generationIndex = illustration.getGenerationIndex() != null ? illustration.getGenerationIndex().toString() : "1";
        String illustrationType = illustration.getIllustrationType() != null ? illustration.getIllustrationType() : "unknown";
        return illustration.getRequestId() + "|" + illustrationType + "|" + generationIndex;
    }

    private boolean isPrivateIllustrationPath(String filePath) {
        return filePath != null && filePath.startsWith("/private-illustrations/");
    }

    /**
     * Get mockups for a specific user (admin only)
     */
    @GetMapping("/users/{userId}/mockups")
    public ResponseEntity<?> getUserMockups(@PathVariable Long userId, @AuthenticationPrincipal OAuth2User principal) {
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

        List<GeneratedMockup> mockups = generatedMockupRepository.findByUserOrderByCreatedAtDesc(targetUser);
        List<MockupDto> mockupDtos = mockups.stream()
                .map(mockup -> new MockupDto(
                        mockup.getFilePath(),
                        mockup.getDescription(),
                        mockup.getMockupType(),
                        mockup.getRequestId()
                ))
                .collect(Collectors.toList());

        log.info("Admin user {} retrieved {} mockups for user {}", adminUser.getEmail(), mockupDtos.size(), targetUser.getEmail());
        return ResponseEntity.ok(mockupDtos);
    }

    /**
     * Get labels for a specific user (admin only)
     */
    @GetMapping("/users/{userId}/labels")
    public ResponseEntity<?> getUserLabels(@PathVariable Long userId, @AuthenticationPrincipal OAuth2User principal) {
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

        List<GeneratedLabel> labels = generatedLabelRepository.findByUserOrderByCreatedAtDesc(targetUser);
        List<LabelDto> labelDtos = labels.stream()
                .map(label -> new LabelDto(
                        label.getFilePath(),
                        label.getLabelText(),
                        label.getServiceSource(),
                        label.getRequestId(),
                        label.getLabelType(),
                        label.getTheme()
                ))
                .collect(Collectors.toList());

        log.info("Admin user {} retrieved {} labels for user {}", adminUser.getEmail(), labelDtos.size(), targetUser.getEmail());
        return ResponseEntity.ok(labelDtos);
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

    /**
     * Get system-wide statistics (admin only)
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User user = customUser.getUser();
        if (!adminService.isAdmin(user)) {
            log.warn("Non-admin user {} attempted to access admin stats endpoint", user.getEmail());
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden - Admin access required"));
        }

        long totalIcons = generatedIconRepository.count();
        long totalIllustrations = generatedIllustrationRepository.count();
        long totalMockups = generatedMockupRepository.count();
        long totalLabels = generatedLabelRepository.count();

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalIcons", totalIcons);
        stats.put("totalIllustrations", totalIllustrations);
        stats.put("totalMockups", totalMockups);
        stats.put("totalLabels", totalLabels);

        log.info("Admin user {} retrieved system stats.", user.getEmail());
        return ResponseEntity.ok(stats);
    }

    /**
     * Get registration and generation stats grouped by day for the requested range.
     */
    @GetMapping("/stats/activity")
    public ResponseEntity<?> getActivityStats(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(required = false) String month,
            @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User user = customUser.getUser();
        if (!adminService.isAdmin(user)) {
            log.warn("Non-admin user {} attempted to access admin activity stats endpoint", user.getEmail());
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden - Admin access required"));
        }

        String normalizedRange = range != null ? range.toLowerCase() : "week";
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate startDate;
        LocalDate endDate;

        if (month != null && !month.isBlank()) {
            try {
                YearMonth yearMonth = YearMonth.parse(month);
                startDate = yearMonth.atDay(1);
                endDate = yearMonth.atEndOfMonth();
                normalizedRange = "month";
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid month format. Use YYYY-MM."));
            }
        } else {
            int days = switch (normalizedRange) {
                case "month" -> 30;
                default -> 7;
            };
            normalizedRange = days == 30 ? "month" : "week";
            endDate = today;
            startDate = endDate.minusDays(days - 1L);
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1L).atStartOfDay();

        Map<LocalDate, Long> registrationMap = toDailyCountMap(
                userRepository.countRegistrationsByDateRange(startDateTime, endDateTime)
        );
        Map<LocalDate, Long> iconMap = toDailyCountMap(
                generatedIconRepository.countGeneratedIconsByDateRange(startDateTime, endDateTime)
        );

        LocalDate seriesEnd = (month != null && !month.isBlank()) ? endDate : today;
        List<DailyCountDto> registrationSeries = buildDailySeries(startDate, seriesEnd, registrationMap);
        List<DailyCountDto> iconSeries = buildDailySeries(startDate, seriesEnd, iconMap);

        long totalRegistrations = registrationSeries.stream().mapToLong(DailyCountDto::getCount).sum();
        long totalIcons = iconSeries.stream().mapToLong(DailyCountDto::getCount).sum();

        ActivityStatsResponse response = new ActivityStatsResponse(
                normalizedRange,
                registrationSeries,
                iconSeries,
                totalRegistrations,
                totalIcons
        );

        log.info("Admin user {} retrieved {} activity stats window starting {} ending {}", user.getEmail(), normalizedRange, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    private Map<LocalDate, Long> toDailyCountMap(List<Object[]> rows) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            Object dateObj = row[0];
            LocalDate date;
            if (dateObj instanceof LocalDate localDate) {
                date = localDate;
            } else if (dateObj instanceof Date sqlDate) {
                date = sqlDate.toLocalDate();
            } else {
                date = LocalDate.parse(dateObj.toString());
            }
            long count = ((Number) row[1]).longValue();
            result.put(date, count);
        }
        return result;
    }

    private List<DailyCountDto> buildDailySeries(LocalDate start, LocalDate end, Map<LocalDate, Long> counts) {
        List<DailyCountDto> series = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            long count = counts.getOrDefault(cursor, 0L);
            series.add(new DailyCountDto(cursor, count));
            cursor = cursor.plusDays(1);
        }
        return series;
    }
}
