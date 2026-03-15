package com.serhatsgr.focusflow_backend.controller;

import com.serhatsgr.focusflow_backend.dto.WeeklyAiResponseDto;
import com.serhatsgr.focusflow_backend.dto.WeeklyHeatmapResponseDto;
import com.serhatsgr.focusflow_backend.service.WeeklyAiCoachService;
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
    private final WeeklyAiCoachService weeklyAiCoachService;

    @GetMapping("/heatmap")
    public ResponseEntity<WeeklyHeatmapResponseDto> getWeeklyHeatmap(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(defaultValue = "UTC") String timezone) {

        String email = principal.getName();
        WeeklyHeatmapResponseDto response = weeklyAnalyticsService.getWeeklyHeatmap(email, referenceDate, timezone);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ai-summary")
    public ResponseEntity<WeeklyAiResponseDto> getWeeklyAiSummary(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {

        String email = principal.getName();
        WeeklyAiResponseDto response = weeklyAiCoachService.getOrGenerateWeeklyAnalysis(email, referenceDate);

        return ResponseEntity.ok(response);
    }
}
