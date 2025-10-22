package com.gosu.iconpackgenerator.domain.letters.dto;

import lombok.Data;

import java.util.List;

@Data
public class LetterPackExportRequest {

    private String requestId;
    private List<String> formats;
    private List<LetterIconPayload> icons;

    @Data
    public static class LetterIconPayload {
        private String letter;
        private String base64Data;
    }
}
