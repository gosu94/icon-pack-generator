package com.gosu.iconpackgenerator.domain.letters.controller;

import com.gosu.iconpackgenerator.domain.icons.component.StreamingStateStore;
import com.gosu.iconpackgenerator.domain.letters.controller.api.LetterPackGenerationControllerAPI;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationRequest;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationResponse;
import com.gosu.iconpackgenerator.domain.letters.service.LetterPackGenerationService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LetterPackGenerationController implements LetterPackGenerationControllerAPI {

    private final LetterPackGenerationService letterPackGenerationService;
    private final StreamingStateStore streamingStateStore;

    @Override
    @ResponseBody
    public CompletableFuture<LetterPackGenerationResponse> generateLetterPack(
            @Valid @org.springframework.web.bind.annotation.RequestBody LetterPackGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!request.isValid()) {
            throw new IllegalArgumentException("Either general description or reference image must be provided");
        }

        if (!(principal instanceof CustomOAuth2User customUser)) {
            throw new SecurityException("User not authenticated");
        }

        User user = customUser.getUser();
        log.info("Letter pack generation request from user {}", user.getEmail());

        CompletableFuture<LetterPackGenerationResponse> future = letterPackGenerationService.generateLetterPack(request, user);

        return future.whenComplete((response, error) -> {
            if (error != null) {
                log.error("Letter pack generation completed with error", error);
            } else if (response != null) {
                streamingStateStore.addRequest(response.getRequestId(), request);
                log.info("Letter pack generation completed for request {}", response.getRequestId());
            }
        });
    }
}
