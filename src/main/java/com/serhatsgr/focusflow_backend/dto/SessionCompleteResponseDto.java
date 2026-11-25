package com.serhatsgr.focusflow_backend.dto;

import com.serhatsgr.focusflow_backend.entities.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Oturum tamamlama yanıt DTO'su.
 * Frontend, oturum bitince bu özeti gösterir (odak skoru, toplam kesinti vb.).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCompleteResponseDto {

    private UUID sessionId;

    /** Frontend'den gelen, doğrulanan gerçek odaklanma süresi. */
    private Integer actualDurationMinutes;

    /**
     * Hesaplanan odak skoru (0–100).
     */
    private Integer focusScore;

    /** Bilişsel Skor Bileşenleri */
    private Integer attentionScore;
    private Integer distractionScore;
    private Integer switchScore;
    private Integer recoveryScore;

    /** Kural tabanlı üretilen geri bildirim metni */
    private String aiAnalysisResult;

    /** Oturuma bağlı toplam kesinti sayısı. */
    private Integer totalInterruptions;

    private SessionStatus status;
}
