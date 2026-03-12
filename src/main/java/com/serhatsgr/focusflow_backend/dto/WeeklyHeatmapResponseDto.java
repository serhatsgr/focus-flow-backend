package com.serhatsgr.focusflow_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyHeatmapResponseDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<HeatmapPointDto> heatmapData;
}
