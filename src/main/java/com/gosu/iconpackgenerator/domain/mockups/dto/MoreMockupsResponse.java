package com.gosu.iconpackgenerator.domain.mockups.dto;

import lombok.Data;

import java.util.List;

@Data
public class MoreMockupsResponse {
    private String requestId;
    private String status;
    private String message;
    private List<MockupGenerationResponse.GeneratedMockup> mockups;
    private Long seed;
}

