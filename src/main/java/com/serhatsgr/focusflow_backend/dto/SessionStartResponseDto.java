package com.serhatsgr.focusflow_backend.dto;

import com.serhatsgr.focusflow_backend.entities.FocusMode;
import com.serhatsgr.focusflow_backend.entities.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Başarıyla başlatılan odaklanma oturumunun tam özeti.
 * Frontend'in timer ekranını başlatmak için ihtiyaç duyduğu tüm veriyi içerir.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStartResponseDto {

    private UUID sessionId;

    /** Oturumun konusu — kullanıcı girmедiyse null. */
    private String topic;

    /** Kullanıcının önceden planladığı süre (dakika). */
    private Integer plannedDurationMinutes;

    /** Seçilen odak modu. */
    private FocusMode mode;

    /** Oturum başlangıç zamanı (UTC). */
    private LocalDateTime startTimeUtc;

    /** Oturum anlık durumu — her zaman ONGOING olarak döner. */
    private SessionStatus status;
}
