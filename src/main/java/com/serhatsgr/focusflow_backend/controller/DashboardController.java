package com.serhatsgr.focusflow_backend.controller;

import com.serhatsgr.focusflow_backend.dto.ApiSuccess;
import com.serhatsgr.focusflow_backend.dto.DashboardResponseDto;
import com.serhatsgr.focusflow_backend.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoint controller'ı.
 *
 * Güvenlik: SecurityConfig'te /api/v1/** → authenticated() kuralı kapsar.
 * Kullanıcı kimliği DashboardService içinde SecurityContextHolder'dan alınır;
 * controller katmanı kullanıcı kimliğine dokunmaz.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Giriş yapmış kullanıcının bugünkü dashboard verilerini döner.
     *
     * GET /api/v1/dashboard/today
     *
     * Yanıt yapısı:
     * {
     * "status": 200,
     * "message": "Dashboard verisi başarıyla getirildi",
     * "data": {
     * "mentalState": { "title": "...", "description": "..." }, // null ise dahil
     * edilmez
     * "activity": { "totalSessions": 3, "totalDurationMinutes": 75,
     * "totalInterruptions": 2 },
     * "sessionOngoing": false
     * }
     * }
     */
    @GetMapping("/today")
    public ResponseEntity<ApiSuccess<DashboardResponseDto>> getTodayDashboard() {
        DashboardResponseDto dashboard = dashboardService.getTodayDashboard();
        return ResponseEntity.ok(ApiSuccess.of("Dashboard verisi başarıyla getirildi", dashboard));
    }
}
