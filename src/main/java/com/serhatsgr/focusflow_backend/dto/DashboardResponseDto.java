package com.serhatsgr.focusflow_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Dashboard ana ekranı için frontend'e döndürülen yanıt DTO'su.
 *
 * mentalState : Bugüne ait bilişsel durum analizi (yoksa null)
 * activity : Bugünkü toplam oturum istatistikleri
 * sessionOngoing : Kullanıcının şu anda aktif bir oturumu var mı?
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardResponseDto {

    private MentalStateDto mentalState;
    private ActivityDto activity;
    private boolean sessionOngoing;

    // ──────────────────────────────────────────────────────────────────────────
    // Inner DTO'lar
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Günlük bilişsel durum özeti.
     * DailyAnalysis tablosunda bugün için kayıt yoksa Dashboard'da null döner.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentalStateDto {
        /** Kısa başlık: "Odaklı", "Yorgun", "Dağınık" vb. */
        private String title;
        /** Detaylı AI açıklaması veya kullanıcı notu. */
        private String description;
    }

    /**
     * Kullanıcının bugünkü toplam aktivite özeti.
     * Oturum yoksa tüm değerler 0 döner (null değil — frontend sıfır gösterir).
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityDto {
        /** Bugün tamamlanan veya devam eden toplam oturum sayısı. */
        private int totalSessions;
        /** Bugünkü tüm oturumların toplam süresi (dakika). */
        private int totalDurationMinutes;
        /** Bugünkü tüm oturumların toplam kesinti (interruption) sayısı. */
        private int totalInterruptions;
    }
}
