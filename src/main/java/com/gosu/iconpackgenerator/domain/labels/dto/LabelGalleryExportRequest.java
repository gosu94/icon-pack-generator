package com.gosu.iconpackgenerator.domain.labels.dto;

import lombok.Data;

import java.util.List;

@Data
public class LabelGalleryExportRequest {
    private List<String> labelFilePaths;
    private List<String> formats;
}

