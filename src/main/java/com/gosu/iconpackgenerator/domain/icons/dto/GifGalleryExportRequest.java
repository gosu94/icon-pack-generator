package com.gosu.iconpackgenerator.domain.icons.dto;

import lombok.Data;

import java.util.List;

@Data
public class GifGalleryExportRequest {
    private List<String> gifFilePaths;
}
