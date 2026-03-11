package com.serhatsgr.focusflow_backend.dto;

import com.serhatsgr.focusflow_backend.entities.FocusMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Oturum başlatma isteği DTO'su (Pre-Session formu).
 *
 * topic : İsteğe bağlı; kullanıcının çalışacağı konu.
 * plannedDurationMinutes : Zorunlu; 1-480 dakika (maks. 8 saat).
 * mode : Zorunlu; odak modu seçimi.
 */
public record SessionStartRequestDto(

        @Size(max = 255, message = "Konu en fazla 255 karakter olabilir") String topic,

        @NotNull(message = "Planlanan süre boş olamaz") @Min(value = 1, message = "Süre en az 1 dakika olmalıdır") @Max(value = 480, message = "Süre en fazla 480 dakika (8 saat) olabilir") Integer plannedDurationMinutes,

        @NotNull(message = "Odak modu seçimi zorunludur") FocusMode mode

) {
}
