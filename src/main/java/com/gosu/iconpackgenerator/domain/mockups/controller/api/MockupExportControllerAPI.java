package com.gosu.iconpackgenerator.domain.mockups.controller.api;

import com.gosu.iconpackgenerator.domain.mockups.dto.MockupExportRequest;
import com.gosu.iconpackgenerator.domain.mockups.dto.MockupElementExportRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;

@RequestMapping("/api/mockups")
public interface MockupExportControllerAPI {
    
    @PostMapping("/export")
    ResponseEntity<byte[]> exportMockups(@RequestBody MockupExportRequest exportRequest);

    @PostMapping("/export-elements")
    ResponseEntity<byte[]> exportMockupElements(@RequestBody MockupElementExportRequest exportRequest,
                                                @AuthenticationPrincipal OAuth2User principal);
}
