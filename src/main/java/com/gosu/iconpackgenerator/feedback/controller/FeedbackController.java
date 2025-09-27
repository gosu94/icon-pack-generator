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

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;
    private final SignalMessageService signalMessageService;

    @PostMapping
    public ResponseEntity<Void> submitFeedback(@RequestBody FeedbackDto feedbackDto, @AuthenticationPrincipal OAuth2User principal) {
        User user = null;
        if (principal instanceof CustomOAuth2User) {
            user = ((CustomOAuth2User) principal).getUser();
        }

        Feedback feedback = new Feedback();
        feedback.setFeedback(feedbackDto.getFeedback());
        feedback.setUser(user);

        feedbackRepository.save(feedback);
        signalMessageService.sendSignalMessage("[IconPackGen] Feedback submitted: " + feedbackDto.getFeedback());

        return ResponseEntity.ok().build();
    }
}
