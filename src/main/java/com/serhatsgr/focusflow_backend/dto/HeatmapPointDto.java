package com.serhatsgr.focusflow_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapPointDto {
    private String dayOfWeek;
    private int hour;
    private int intensity;
}
