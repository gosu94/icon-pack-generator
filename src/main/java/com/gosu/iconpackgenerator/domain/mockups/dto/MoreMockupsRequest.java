package com.gosu.iconpackgenerator.domain.mockups.dto;

import lombok.Data;

@Data
public class MoreMockupsRequest {
    private String originalRequestId;
    private String originalImageBase64;
    private String description;
    private Long seed;
}

