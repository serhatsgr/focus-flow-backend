package com.serhatsgr.focusflow_backend.dto;

import com.serhatsgr.focusflow_backend.entities.FocusMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Flutter'ın offline iken oluşturduğu tek bir oturum kaydını temsil eder.
 *
 * localId: Flutter'ın geçici UUID'si — sunucu tarafında kalıcı değil, yalnızca
 * yanıtta eşleştirme için kullanılır.
 */
public record OfflineSessionDto(

        @NotBlank(message = "localId boş olamaz")
        String localId,

        @NotNull(message = "Oturum başlangıç zamanı boş olamaz")
        LocalDateTime startTimeUtc,

        @NotNull(message = "Oturum bitiş zamanı boş olamaz")
        LocalDateTime endTimeUtc,

        @NotNull(message = "Oturum konusu boş olamaz")
        String topic,

        String mode, // Enum yerine String (Güvenli parse için)

        @NotNull(message = "Oturum süresi boş olamaz")
        @Min(value = 1, message = "Oturum süresi en az 1 dakika olmalı")
        Integer durationMinutes,

        @NotNull(message = "Gerçekleşen süre boş olamaz")
        @Min(value = 0, message = "Gerçekleşen süre negatif olamaz")
        Integer actualDurationMinutes,

        @NotNull(message = "Planlanan süre boş olamaz")
        @Min(value = 0, message = "Planlanan süre negatif olamaz")
        Integer plannedDurationMinutes,

        @Valid
        List<InterruptionRequestDto> interruptions,

        // Giyilebilir cihaz verileri — opsiyonel
        Boolean hasWearableData,
        Double avgHeartRate,
        Double hrVariance

) {
}
