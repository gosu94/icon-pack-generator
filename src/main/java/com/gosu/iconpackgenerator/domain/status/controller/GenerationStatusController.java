package com.gosu.iconpackgenerator.domain.status.controller;

import com.gosu.iconpackgenerator.domain.status.GenerationStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class GenerationStatusController {

    private final GenerationStatusService generationStatusService;

    @GetMapping("/generation")
    public GenerationStatusService.GenerationStatusResponse getGenerationStatus() {
        return generationStatusService.getStatus();
    }
}
