package com.gosu.icon_pack_generator.dto;

import lombok.Data;

import java.util.List;

@Data
public class IconExportRequest {
    
    private String requestId;
    private List<IconGenerationResponse.GeneratedIcon> icons;
}
