package com.serhatsgr.focusflow_backend.controller;

import com.serhatsgr.focusflow_backend.dto.DailyReportResponseDto;
import com.serhatsgr.focusflow_backend.service.DailyAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class DailyAnalyticsController {

    private final DailyAnalyticsService dailyAnalyticsService;

    @GetMapping("/daily")
    public ResponseEntity<DailyReportResponseDto> getDailyReport(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        String email = principal.getName();
        DailyReportResponseDto report = dailyAnalyticsService.getDailyReport(email, date);

        return ResponseEntity.ok(report);
    }
}
