package com.gosu.iconpackgenerator.domain.icons.dto;

import lombok.Data;
import java.util.List;

@Data
public class GalleryExportRequest {
    private List<String> iconFilePaths;
    private List<String> formats;
    private boolean vectorizeSvg;
    private boolean hqUpscale;
}
