package com.serhatsgr.focusflow_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Oturum tamamlama isteği DTO'su.
 *
 * actualDurationMinutes : Frontend'in ölçtüğü gerçek odaklanma süresi
 * (zorunlu).
 * interruptions : Oturum boyunca yaşanan kesinti listesi (boş olabilir).
 * hasWearableData : Akıllı saat verisi gönderilip gönderilmediği.
 * avgHeartRate : Oturumdaki ortalama kalp atış hızı (bpm).
 * hrVariance : Kalp atış hızı varyansı (bpm²).
 */
public record SessionCompleteRequestDto(

        @NotNull(message = "Gerçek süre boş olamaz") @Min(value = 0, message = "Süre negatif olamaz") Integer actualDurationMinutes,

        /**
         * Kesinti yoksa boş liste gönderilebilir — null gelirse de güvenle handle
         * edilir.
         */
        @Valid List<InterruptionRequestDto> interruptions,

        // ── Biyometrik / Giyilebilir Cihaz Verileri ────────────────────────────
        // hasWearableData true ise avgHeartRate ve hrVariance alanları beklenir.
        // false veya null ise bu alanlar göz ardı edilir; fizyolojik skor hesaplanmaz.

        /** Akıllı saat bağlantısı aktif miydi? */
        Boolean hasWearableData,

        /**
         * Oturumdaki ortalama kalp atış hızı (bpm). hasWearableData = true ise anlamlı.
         */
        Double avgHeartRate,

        /** Kalp atış hızı varyansı (bpm²). hasWearableData = true ise anlamlı. */
        Double hrVariance

) {
    /** Null listeyi güvenli şekilde boş liste olarak normalize et. */
    public List<InterruptionRequestDto> interruptions() {
        return interruptions != null ? interruptions : Collections.emptyList();
    }
}
