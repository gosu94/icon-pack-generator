package com.gosu.iconpackgenerator.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class GalleryExportRequest {
    private List<String> iconFilePaths;
    private List<String> formats;
}