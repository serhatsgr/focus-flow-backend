package com.serhatsgr.focusflow_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Session Summary ekranı için frontend'e dönen özet DTO'su.
 *
 * isEarlyExit : actualDurationMinutes < plannedDurationMinutes ise true
 * focusStability : focusScore'a göre "Yüksek" (>=80) / "Orta" (50-79) / "Düşük"
 * (<50)
 * analysisText : Entity'deki aiAnalysisResult — henüz yoksa null, frontend
 * handle eder
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryResponseDto {

    private UUID sessionId;
    private Integer actualDurationMinutes;
    private Integer plannedDurationMinutes;
    private Integer interruptionsCount;
    private Boolean earlyExit;
    private String focusStability;
    private Integer focusScore;

    /** AI analiz metni. DB'de henüz yazılmamışsa null döner. */
    private String analysisText;
}
