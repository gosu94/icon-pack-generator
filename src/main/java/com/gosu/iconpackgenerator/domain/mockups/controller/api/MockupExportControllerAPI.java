package com.gosu.iconpackgenerator.domain.mockups.controller.api;

import com.gosu.iconpackgenerator.domain.mockups.dto.MockupExportRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/mockups")
public interface MockupExportControllerAPI {
    
    @PostMapping("/export")
    ResponseEntity<byte[]> exportMockups(@RequestBody MockupExportRequest exportRequest);
}

