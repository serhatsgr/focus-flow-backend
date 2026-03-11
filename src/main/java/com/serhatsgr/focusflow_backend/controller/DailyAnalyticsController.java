package com.serhatsgr.focusflow_backend.controller;

import com.serhatsgr.focusflow_backend.dto.DailyReportResponseDto;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.repository.FocusSessionRepository;
import com.serhatsgr.focusflow_backend.service.DailyAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class DailyAnalyticsController {

    private final DailyAnalyticsService dailyAnalyticsService;
    private final FocusSessionRepository focusSessionRepository;

    @GetMapping("/daily")
    public ResponseEntity<DailyReportResponseDto> getDailyReport(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        String username = principal.getName();
        
        // Gün başlangıcı 00:00:00 ve gün bitişi 23:59:59 hesaplaması
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<FocusSession> dailySessions = focusSessionRepository.findAllByUser_UsernameAndStartTimeUtcBetween(username, startOfDay, endOfDay);
        DailyReportResponseDto report = dailyAnalyticsService.generateDailyReport(dailySessions);

        return ResponseEntity.ok(report);
    }
}
