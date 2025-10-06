package com.gosu.iconpackgenerator.domain.illustrations.controller.api;

import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationExportRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/illustrations")
public interface IllustrationExportControllerAPI {
    
    @PostMapping("/export")
    ResponseEntity<byte[]> exportIllustrations(@RequestBody IllustrationExportRequest exportRequest);
}

