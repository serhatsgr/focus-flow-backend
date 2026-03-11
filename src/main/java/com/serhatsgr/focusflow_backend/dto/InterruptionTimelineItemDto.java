package com.serhatsgr.focusflow_backend.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Tek bir kesintinin zaman çizelgesi görünümü.
 *
 * time : Kesintinin lokal saati (UTC+3, HH:mm formatı). timestampUtc
 * null ise bu alan da null döner.
 * displayTitle : Frontend'e doğrudan gösterilecek Türkçe kesinti metni.
 * durationFormatted: "45 sn" veya "2 dk 10 sn" formatında okunabilir süre.
 * durationSeconds null ise null döner.
 */
@Value
@Builder
public class InterruptionTimelineItemDto {

    /** UTC+3 ile hesaplanmış kesinti saati; örn: "08:14". */
    String time;

    /** Kesinti türü ve uygulama adından üretilen okunabilir Türkçe başlık. */
    String displayTitle;

    /** İnsan okunabilir süre; örn: "45 sn" veya "2 dk 10 sn". */
    String durationFormatted;
}
