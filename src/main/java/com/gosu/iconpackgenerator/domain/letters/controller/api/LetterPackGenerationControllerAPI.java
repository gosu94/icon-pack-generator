package com.gosu.iconpackgenerator.domain.letters.controller.api;

import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationRequest;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackGenerationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.concurrent.CompletableFuture;

@RequestMapping("/api/letters")
public interface LetterPackGenerationControllerAPI {

    @PostMapping("/generate")
    CompletableFuture<LetterPackGenerationResponse> generateLetterPack(
            @Valid @RequestBody LetterPackGenerationRequest request,
            @AuthenticationPrincipal OAuth2User principal
    );
}
