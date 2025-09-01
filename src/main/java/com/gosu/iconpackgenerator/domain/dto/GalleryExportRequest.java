package com.gosu.iconpackgenerator.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class GalleryExportRequest {
    private List<String> iconFilePaths;
    
    // Icons already have transparent backgrounds, multiple formats are generated automatically
}
