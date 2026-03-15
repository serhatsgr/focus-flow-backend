package com.serhatsgr.focusflow_backend.dto;

import com.serhatsgr.focusflow_backend.entities.InterruptionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Tek bir kesinti kaydı DTO'su.
 * Frontend, oturumu bitirirken bu listeyi SessionCompleteRequestDto içinde
 * gönderir. OffsetDateTime veya string olarak gelebilir.
 */
public record InterruptionRequestDto(

                @NotNull(message = "Kesinti türü boş olamaz") 
                String type, // Enum yerine String

                /** Kesintiye neden olan uygulama adı — APP_SWITCH için anlamlı. */
                String appName,

                @Min(value = 0, message = "Kesinti süresi negatif olamaz") 
                Integer durationSeconds,

                /** Kesinti sonrası işe dönme süresi. */
                @Min(value = 0, message = "Toparlanma süresi negatif olamaz") 
                Integer recoveryTimeSeconds,

                LocalDateTime timestampUtc // Ekleme
) {
}
