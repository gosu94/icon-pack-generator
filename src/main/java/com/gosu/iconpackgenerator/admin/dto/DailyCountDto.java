package com.gosu.iconpackgenerator.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyCountDto {
    private LocalDate date;
    private long count;
}
