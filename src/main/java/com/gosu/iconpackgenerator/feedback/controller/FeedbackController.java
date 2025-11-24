package com.gosu.iconpackgenerator.feedback.controller;

import com.gosu.iconpackgenerator.feedback.dto.FeedbackDto;
import com.gosu.iconpackgenerator.feedback.model.Feedback;
import com.gosu.iconpackgenerator.feedback.repository.FeedbackRepository;
import com.gosu.iconpackgenerator.singal.SignalMessageService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;
    private final SignalMessageService signalMessageService;

    @PostMapping
    public ResponseEntity<?> submitFeedback(@RequestBody FeedbackDto feedbackDto, @AuthenticationPrincipal OAuth2User principal) {
        User user = null;
        if (principal instanceof CustomOAuth2User) {
            user = ((CustomOAuth2User) principal).getUser();
        }

        if (user != null) {
            LocalDateTime startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
            boolean alreadySubmitted = feedbackRepository.existsByUserAndCreatedAtAfter(user, startOfToday);
            if (alreadySubmitted) {
                return ResponseEntity.status(429)
                        .body(Map.of("message", "You can submit feedback once per day."));
            }
        }

        Feedback feedback = new Feedback();
        feedback.setFeedback(feedbackDto.getFeedback());
        feedback.setUser(user);

        feedbackRepository.save(feedback);
        signalMessageService.sendSignalMessage("[IconPackGen] Feedback submitted: " + feedbackDto.getFeedback());

        return ResponseEntity.ok().build();
    }
}
