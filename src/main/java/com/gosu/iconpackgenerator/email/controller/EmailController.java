package com.gosu.iconpackgenerator.email.controller;

import com.gosu.iconpackgenerator.admin.service.AdminService;
import com.gosu.iconpackgenerator.email.dto.AdminEmailRequest;
import com.gosu.iconpackgenerator.email.service.EmailService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/email")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<?> sendEmail(
            @RequestBody AdminEmailRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!(principal instanceof CustomOAuth2User customUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User adminUser = customUser.getUser();
        if (!adminService.isAdmin(adminUser)) {
            log.warn("Non-admin user {} attempted to send admin email", adminUser.getEmail());
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden - Admin access required"));
        }

        String subject = request.getSubject() != null ? request.getSubject().trim() : "";
        String htmlBody = request.getHtmlBody() != null ? request.getHtmlBody().trim() : "";

        if (subject.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject cannot be empty"));
        }

        if (htmlBody.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email body cannot be empty"));
        }

        AdminEmailRequest.RecipientScope scope = request.getRecipientScopeOrDefault();

        LinkedHashSet<String> recipientSet = new LinkedHashSet<>();
        String configuredAdminEmail = adminService.getConfiguredAdminEmail();
        if (scope == AdminEmailRequest.RecipientScope.ME) {
            if (configuredAdminEmail != null) {
                recipientSet.add(configuredAdminEmail);
            } else if (adminUser.getEmail() != null) {
                recipientSet.add(adminUser.getEmail().trim());
            }
        } else {
            userRepository.findAll().stream()
                    .map(User::getEmail)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(email -> !email.isBlank())
                    .forEach(recipientSet::add);
            if (configuredAdminEmail != null) {
                recipientSet.add(configuredAdminEmail);
            }
        }

        if (recipientSet.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No recipients found to send the email"));
        }

        List<String> failedRecipients = new ArrayList<>();
        recipientSet.forEach(email -> {
            boolean sent = emailService.sendCustomEmail(email, subject, htmlBody);
            if (!sent) {
                failedRecipients.add(email);
            }
        });

        int total = recipientSet.size();
        int failed = failedRecipients.size();
        int successful = total - failed;

        log.info("Admin {} initiated email send to {} recipients. Successful: {}, Failed: {}",
                adminUser.getEmail(), total, successful, failed);

        if (!failedRecipients.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Email sent to some recipients, but failures occurred",
                    "successfulCount", successful,
                    "failedCount", failed,
                    "failedRecipients", failedRecipients
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Email sent successfully",
                "recipientCount", total
        ));
    }
}
