package com.serhatsgr.focusflow_backend.dto;

import com.serhatsgr.focusflow_backend.entities.SessionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Oturum durumu güncelleme isteği (Duraklatma / Devam Ettirme).
 * ONGOING → PAUSED : Duraklatma
 * PAUSED → ONGOING : Devam ettirme
 */
public record SessionStatusUpdateRequestDto(

        @NotNull(message = "Oturum durumu boş olamaz") SessionStatus status

) {
}
