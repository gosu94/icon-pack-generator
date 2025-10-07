package com.gosu.iconpackgenerator.domain.illustrations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IllustrationDto {
    private String imageUrl;
    private String description;
    private String serviceSource;
    private String requestId;
}
