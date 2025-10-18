package com.gosu.iconpackgenerator.domain.mockups.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MockupDto {
    private String imageUrl;
    private String description;
    private String mockupType;
    private String requestId;
}