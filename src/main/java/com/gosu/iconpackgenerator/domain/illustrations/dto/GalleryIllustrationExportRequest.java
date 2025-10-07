package com.gosu.iconpackgenerator.domain.illustrations.dto;

import lombok.Data;

import java.util.List;

@Data
public class GalleryIllustrationExportRequest {
    private List<String> illustrationFilePaths;
    private List<String> formats; // e.g., ["png", "webp"]
    private List<Integer> sizes; // e.g., [250, 500, 1000]
}

