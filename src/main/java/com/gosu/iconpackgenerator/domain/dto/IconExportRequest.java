package com.gosu.iconpackgenerator.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class IconExportRequest {

    private String requestId;
    private String serviceName;
    private int generationIndex;
    private List<IconGenerationResponse.GeneratedIcon> icons;
    private boolean removeBackground = true;
    private String outputFormat = "png";

}
