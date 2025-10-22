package com.gosu.iconpackgenerator.domain.letters.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LetterPackGenerationResponse {

    private String status;
    private String message;
    private String requestId;
    private List<LetterGroup> groups = new ArrayList<>();

    @Data
    public static class LetterGroup {
        private String name;
        private List<LetterIcon> icons = new ArrayList<>();
        private String originalGridImageBase64;
    }

    @Data
    public static class LetterIcon {
        private String letter;
        private String base64Data;
        private int sequence;
    }
}
