package com.gosu.iconpackgenerator.domain.mockups.dto;

import lombok.Data;

import java.util.List;

@Data
public class MockupExportRequest {
    private String requestId;
    private String serviceName;
    private Integer generationIndex = 1;
    private List<MockupGenerationResponse.GeneratedMockup> mockups;
    private List<String> formats; // e.g., ["png", "jpg"]
    private List<Integer> sizes; // e.g., [1920, 2560] - widths in pixels
}

