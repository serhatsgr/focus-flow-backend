package com.serhatsgr.focusflow_backend.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Oturum kesinti zaman çizelgesi yanıt DTO'su.
 *
 * Mobil "Interruption Timeline" ekranının ihtiyaç duyduğu tüm verileri
 * düz, formatlı şekilde taşır; ekran herhangi bir dönüşüm yapmak zorunda
 * kalmaz.
 *
 * focusScore : finalFocusScore'un tamsayı hali (null → null).
 * sessionStartTime : Oturumun UTC+3 başlangıç saati ("HH:mm").
 * sessionEndTime : Oturumun UTC+3 bitiş saati ("HH:mm"). Oturum sürdüyse null.
 * interruptions : timestampUtc'ye göre ESKİDEN YENİYE sıralı kesinti listesi.
 * timelineAnalysis : AI Coach analiz metni (henüz üretilmemişse null).
 */
@Value
@Builder
public class SessionTimelineResponseDto {

    /** Oturumun nihai odak skoru (0-100). */
    Integer focusScore;

    /** Oturum başlangıç saati — UTC+3, "HH:mm" formatı; örn: "08:00". */
    String sessionStartTime;

    /**
     * Oturum bitiş saati — UTC+3, "HH:mm" formatı; örn: "09:00".
     * Tamamlanmamış oturumlarda null döner.
     */
    String sessionEndTime;

    /** Kronolojik sıraya göre kesinti öğeleri listesi. */
    List<InterruptionTimelineItemDto> interruptions;

    /** AI Coach'un oturum analiz metni; henüz üretilmemişse null. */
    String timelineAnalysis;
}
