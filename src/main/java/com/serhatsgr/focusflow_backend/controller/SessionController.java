package com.serhatsgr.focusflow_backend.controller;

import com.serhatsgr.focusflow_backend.dto.ApiSuccess;
import com.serhatsgr.focusflow_backend.dto.SessionCompleteRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionCompleteResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionStartRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionStartResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionStatusUpdateRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionSummaryResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionTimelineResponseDto;
import com.serhatsgr.focusflow_backend.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Odaklanma oturumu yönetim controller'ı.
 *
 * Güvenlik: Tüm endpoint'ler SecurityConfig → authenticated() kapsamındadır.
 * Oturum sahipliği SessionService → findByIdAndUserId ile doğrulanır.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** POST /api/v1/sessions/start — Oturum Başlatma (201 Created) */
    @PostMapping("/start")
    public ResponseEntity<ApiSuccess<SessionStartResponseDto>> startSession(
            @Valid @RequestBody SessionStartRequestDto request) {

        SessionStartResponseDto response = sessionService.startSession(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiSuccess.of(201, "Odaklanma oturumu başlatıldı", response));
    }

    /**
     * PATCH /api/v1/sessions/{sessionId}/status — Duraklatma / Devam Ettirme (200
     * OK)
     */
    @PatchMapping("/{sessionId}/status")
    public ResponseEntity<ApiSuccess<SessionStartResponseDto>> updateSessionStatus(
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody SessionStatusUpdateRequestDto request) {

        SessionStartResponseDto response = sessionService.updateSessionStatus(sessionId, request);
        return ResponseEntity.ok(ApiSuccess.of("Oturum durumu güncellendi", response));
    }

    /** PATCH /api/v1/sessions/{sessionId}/complete — Oturumu Tamamla (200 OK) */
    @PatchMapping("/{sessionId}/complete")
    public ResponseEntity<ApiSuccess<SessionCompleteResponseDto>> completeSession(
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody SessionCompleteRequestDto request) {

        SessionCompleteResponseDto response = sessionService.completeSession(sessionId, request);
        return ResponseEntity.ok(ApiSuccess.of("Oturum başarıyla tamamlandı", response));
    }

    /**
     * GET /api/v1/sessions/{sessionId}/summary — Session Summary (200 OK)
     *
     * Yanıt yapısı:
     * {
     * "data": {
     * "sessionId": "...",
     * "actualDurationMinutes": 42,
     * "plannedDurationMinutes": 45,
     * "interruptionsCount": 3,
     * "earlyExit": true,
     * "focusStability": "Orta",
     * "focusScore": 85,
     * "analysisText": null // AI analizi henüz yoksa null
     * }
     * }
     */
    @GetMapping("/{sessionId}/summary")
    public ResponseEntity<ApiSuccess<SessionSummaryResponseDto>> getSessionSummary(
            @PathVariable("sessionId") UUID sessionId) {

        SessionSummaryResponseDto response = sessionService.getSessionSummary(sessionId);
        return ResponseEntity.ok(ApiSuccess.of("Oturum özeti getirildi", response));
    }

    /**
     * GET /api/v1/sessions/{sessionId}/timeline — Interruption Timeline (200 OK)
     *
     * Mobil arayüzdeki "Kesinti Zaman Çizelgesi" ekranı için gerekli,
     * baştan sona formatlanmış verileri döner.
     */
    @GetMapping("/{sessionId}/timeline")
    public ResponseEntity<ApiSuccess<SessionTimelineResponseDto>> getSessionTimeline(
            @PathVariable("sessionId") UUID sessionId) {

        SessionTimelineResponseDto response = sessionService.getSessionTimeline(sessionId);
        return ResponseEntity.ok(ApiSuccess.of("Kesinti zaman çizelgesi getirildi", response));
    }
}
