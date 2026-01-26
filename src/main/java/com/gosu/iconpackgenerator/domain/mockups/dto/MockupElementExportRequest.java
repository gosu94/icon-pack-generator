package com.gosu.iconpackgenerator.domain.mockups.dto;

import lombok.Data;

import java.util.List;

@Data
public class MockupElementExportRequest {
    private String requestId;
    private List<String> formats;
    private boolean vectorizeSvg;
    private boolean hqUpscale;
}
