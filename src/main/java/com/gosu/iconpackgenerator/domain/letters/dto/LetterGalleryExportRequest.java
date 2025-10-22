package com.gosu.iconpackgenerator.domain.letters.dto;

import lombok.Data;

import java.util.List;

@Data
public class LetterGalleryExportRequest {

    private List<String> letterFilePaths;
    private List<String> formats;
}
