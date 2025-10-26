package com.gosu.iconpackgenerator.domain.labels.dto;

import lombok.Data;

import java.util.List;

@Data
public class LabelExportRequest {
    private String requestId;
    private String serviceName;
    private int generationIndex;
    private List<LabelGenerationResponse.GeneratedLabel> labels;
    private List<String> formats;
    private boolean vectorizeSvg;
}
