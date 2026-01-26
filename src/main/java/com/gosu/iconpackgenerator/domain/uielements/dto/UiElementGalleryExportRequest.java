package com.gosu.iconpackgenerator.domain.uielements.dto;

import lombok.Data;

import java.util.List;

@Data
public class UiElementGalleryExportRequest {
    private List<String> uiElementFilePaths;
    private List<String> formats;
    private boolean vectorizeSvg;
    private boolean hqUpscale;
}
