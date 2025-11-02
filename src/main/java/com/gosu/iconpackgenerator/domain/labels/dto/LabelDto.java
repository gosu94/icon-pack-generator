package com.gosu.iconpackgenerator.domain.labels.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabelDto {
    private String imageUrl;
    private String labelText;
    private String serviceSource;
    private String requestId;
    private String labelType;
    private String theme;
}

