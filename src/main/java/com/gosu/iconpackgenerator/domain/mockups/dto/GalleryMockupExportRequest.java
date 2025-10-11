package com.gosu.iconpackgenerator.domain.mockups.dto;

import lombok.Data;

import java.util.List;

@Data
public class GalleryMockupExportRequest {
    private List<String> mockupFilePaths;
    private List<String> formats; // e.g., ["png", "jpg"]
    private List<Integer> sizes; // e.g., [1920, 2560] - widths in pixels
}

