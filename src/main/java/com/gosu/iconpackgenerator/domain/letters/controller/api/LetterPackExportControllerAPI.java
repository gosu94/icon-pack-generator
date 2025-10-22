package com.gosu.iconpackgenerator.domain.letters.controller.api;

import com.gosu.iconpackgenerator.domain.letters.dto.LetterGalleryExportRequest;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackExportRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/letters")
public interface LetterPackExportControllerAPI {

    @PostMapping("/export")
    ResponseEntity<byte[]> exportLetterPack(@Valid @RequestBody LetterPackExportRequest request,
                                            @AuthenticationPrincipal OAuth2User principal);

    @PostMapping("/export/gallery")
    ResponseEntity<byte[]> exportLettersFromGallery(@Valid @RequestBody LetterGalleryExportRequest request,
                                                    @AuthenticationPrincipal OAuth2User principal);
}
