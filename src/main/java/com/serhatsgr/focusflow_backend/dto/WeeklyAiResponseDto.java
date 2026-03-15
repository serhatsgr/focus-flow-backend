package com.serhatsgr.focusflow_backend.dto;

import lombok.Builder;

import java.util.List;

/**
 * Haftalık Nöro-Verimlilik Yapay Zeka Yanıt DTO'su.
 * NEUROCOACH motorunun ürettiği haftalık analiz raporu.
 */
@Builder
public record WeeklyAiResponseDto(
        String weeklyTheme,
        String weeklySummary,
        List<String> strengths,
        List<String> weaknesses,
        String actionableAdvice) {
}
