package com.gosu.iconpackgenerator.domain.icons.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload returned once GIF generation finishes.
 */
@Data
public class GifGenerationResponse {

    private String gifRequestId;
    private String requestId;
    private String status;
    private String message;
    private List<GifAsset> gifs = new ArrayList<>();

    @Data
    public static class GifAsset {
        private Long id;
        private String iconId;
        private String fileName;
        private String filePath;
        private String iconType;
        private String serviceSource;
        private Integer gridPosition;
        private Integer generationIndex;
    }
}
