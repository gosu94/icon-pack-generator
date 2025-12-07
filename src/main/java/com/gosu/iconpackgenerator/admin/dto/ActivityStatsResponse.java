package com.gosu.iconpackgenerator.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActivityStatsResponse {
    private String range;
    private List<DailyCountDto> registrations;
    private List<DailyCountDto> icons;
    private long totalRegistrations;
    private long totalIcons;
}
