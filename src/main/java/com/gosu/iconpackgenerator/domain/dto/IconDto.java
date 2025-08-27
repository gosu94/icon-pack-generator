package com.gosu.iconpackgenerator.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IconDto {
    private String imageUrl;
    private String description;
    private String serviceSource;
    private String requestId;
    private String iconType;
    private String theme;
}
