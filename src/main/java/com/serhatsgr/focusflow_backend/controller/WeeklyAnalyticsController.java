package com.serhatsgr.focusflow_backend.controller;

import com.serhatsgr.focusflow_backend.dto.WeeklyHeatmapResponseDto;
import com.serhatsgr.focusflow_backend.service.WeeklyAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics/weekly")
@RequiredArgsConstructor
public class WeeklyAnalyticsController {

    private final WeeklyAnalyticsService weeklyAnalyticsService;

    @GetMapping("/heatmap")
    public ResponseEntity<WeeklyHeatmapResponseDto> getWeeklyHeatmap(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(defaultValue = "UTC") String timezone) {

        String email = principal.getName();
        WeeklyHeatmapResponseDto response = weeklyAnalyticsService.getWeeklyHeatmap(email, referenceDate, timezone);

        return ResponseEntity.ok(response);
    }
}
