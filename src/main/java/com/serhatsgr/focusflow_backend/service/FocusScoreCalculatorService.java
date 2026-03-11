package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.entities.FocusMode;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.entities.Interruption;
import com.serhatsgr.focusflow_backend.entities.InterruptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Harmanlanmış Bilişsel + Fizyolojik Odak Skoru Hesaplama Motoru — Faz 2 +
 * Biyofeedback.
 *
 * Skor mimarisi:
 *
 * ┌─────────────────────────────────────────────────────┐
 * │ Bilişsel Skor (0–100, 5 bileşen) │
 * │ Attention 25 puan Distraction 25 puan │
 * │ Switch 20 puan Recovery 15 puan │
 * │ Quality 15 puan │
 * ├─────────────────────────────────────────────────────┤
 * │ Fizyolojik Skor (0–100, giyilebilir cihaz varsa) │
 * │ Arousal Score 40% (Ters-U eğrisi, HR bazlı) │
 * │ Stability Score 60% (HR varyans bazlı) │
 * ├─────────────────────────────────────────────────────┤
 * │ Harmanlama │
 * │ Wearable var → bilişsel × 0.80 + fizyolojik × 0.20 │
 * │ Wearable yok → bilişsel × 1.00 │
 * └─────────────────────────────────────────────────────┘
 */
@Service
public class FocusScoreCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(FocusScoreCalculatorService.class);

    // ─── Bilişsel skor ağırlıkları ────────────────────────────────────────────
    private static final double WEIGHT_ATTENTION = 25.0;
    private static final double WEIGHT_DISTRACTION = 25.0;
    private static final double WEIGHT_SWITCH = 20.0;
    private static final double WEIGHT_RECOVERY = 15.0;
    private static final double WEIGHT_QUALITY = 15.0;

    // ─── Bilişsel formül sabitleri ────────────────────────────────────────────
    private static final double ATTENTION_HALF_SATURATION = 20.0;
    private static final double DISTRACTION_DECAY = 10.0;
    private static final double SWITCH_DECAY = 15.0;
    private static final double RECOVERY_DECAY_SECONDS = 120.0;
    private static final double QUALITY_OPTIMAL_MINUTES = 40.0;
    private static final double QUALITY_GAUSSIAN_VARIANCE = 800.0;

    // ─── Fizyolojik formül sabitleri ─────────────────────────────────────────
    /** Optimal nabız = dinlenik nabız × bu çarpan (%15 üstü = aktif-sakin zon). */
    private static final double OPTIMAL_HR_MULTIPLIER = 1.15;
    /** Arousal eğrisi genişliği (bpm²). Dar eğri → optimal zona hassas. */
    private static final double AROUSAL_GAUSSIAN_VARIANCE = 450.0;
    /** HR varyans baskılama sabiti. 15.0 → varyans 15 bpm² olunca %37 puan. */
    private static final double STABILITY_DECAY = 15.0;
    /** Fizyolojik ağırlıklar. */
    private static final double PHYSIO_AROUSAL_WEIGHT = 0.4;
    private static final double PHYSIO_STABILITY_WEIGHT = 0.6;
    /** Harmanlama ağırlıkları. */
    private static final double BLEND_COGNITIVE = 0.8;
    private static final double BLEND_PHYSIO = 0.2;
    /** Varsayılan dinlenik nabız (kullanıcı verisi yoksa). */
    private static final int DEFAULT_RESTING_HR = 70;

    // ──────────────────────────────────────────────────────────────────────────
    // Ana Metot
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verilen oturumun tüm metrik ve skorlarını hesaplayıp IN-PLACE atar.
     * Kaydetme (repository.save()) sorumluluğu çağırana aittir.
     *
     * @param session Hesaplamanın yapılacağı oturum (null olmamalı)
     */
    public void calculateAndSetScores(FocusSession session) {
        if (session == null) {
            log.warn("calculateAndSetScores: null session — işlem atlandı.");
            return;
        }

        int sessionDuration = resolveSessionDuration(session);
        if (sessionDuration <= 0) {
            log.debug("Session {} süresi ≤0 — tüm skorlar sıfırlanıyor.", session.getId());
            zeroOutAllScores(session);
            return;
        }

        // ─── Ceza Çarpanı — Odak moduna göre kesinti toleransı ───────────────
        // STRICT → 1.5 (Sıfır tolerans, ağır ceza)
        // FLEXIBLE → 0.5 (Yüksek tolerans, hafif ceza)
        // DEEP/null → 1.0 (Standart)
        double penaltyMultiplier = resolvePenaltyMultiplier(session.getMode());
        log.debug("Session {} | mode={} | penaltyMultiplier={}",
                session.getId(), session.getMode(), penaltyMultiplier);

        // ─── ADIM 1: Türetilmiş metrikler ────────────────────────────────────
        DerivedMetrics metrics = computeDerivedMetrics(session, sessionDuration);
        session.setAppSwitchCount(metrics.appSwitchCount());
        session.setAvgFocusBlockMinutes(metrics.avgFocusBlockMinutes());
        session.setAvgRecoveryTimeSeconds(metrics.avgRecoveryTimeSeconds());

        // ─── ADIM 2: Bilişsel skor bileşenleri ───────────────────────────────
        double attention = computeAttentionScore(metrics.avgFocusBlockMinutes());
        double distraction = computeDistractionScore(metrics.distractionRate(), penaltyMultiplier);
        double switching = computeSwitchScore(metrics.taskSwitchRate(), penaltyMultiplier);
        double recovery = computeRecoveryScore(metrics.avgRecoveryTimeSeconds(), penaltyMultiplier);
        double quality = computeQualityScore(sessionDuration);

        session.setAttentionScore(attention);
        session.setDistractionScore(distraction);
        session.setSwitchScore(switching);
        session.setRecoveryScore(recovery);
        session.setQualityScore(quality);

        double totalCognitiveScore = attention + distraction + switching + recovery + quality;

        // ─── ADIM 3: Fizyolojik harmanlama ───────────────────────────────────
        double finalScore = blendWithPhysio(session, totalCognitiveScore);

        // ─── ADIM 4: Final skoru sınırla ve kaydet ───────────────────────────
        double clamped = Math.min(100.0, Math.max(0.0, finalScore));
        session.setFinalFocusScore(clamped);

        log.debug("Session {} | cog={:.1f} | wearable={} | final={:.1f}",
                session.getId(), totalCognitiveScore,
                Boolean.TRUE.equals(session.getHasWearableData()), clamped);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Fizyolojik Harmanlama
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Giyilebilir cihaz verisi varsa bilişsel skoru fizyolojik skorla harmanlayarak
     * döner; yoksa bilişsel skoru olduğu gibi döner.
     *
     * Null/güvenlik kontrolleri:
     * - hasWearableData null → false kabul edilir.
     * - avgHeartRate null → wearable verisi yok sayılır (güvenli fallback).
     * - hrVariance null → 0.0 kabul edilir (tam stabilite = kullanıcı lehine).
     *
     * @param session             Fizyolojik alanlara erişim için oturum
     * @param totalCognitiveScore Bilişsel skor toplamı (0–100 aralığına henüz
     *                            sınırlanmamış)
     * @return Harmanlanmış veya salt bilişsel skor
     */
    private double blendWithPhysio(FocusSession session, double totalCognitiveScore) {
        boolean hasWearable = Boolean.TRUE.equals(session.getHasWearableData())
                && session.getAvgHeartRate() != null; // null HR → wearable yok say

        if (!hasWearable) {
            return totalCognitiveScore; // Wearable yok → bilişsel %100 ağırlık
        }

        // Optimal nabız = dinlenik nabız × 1.15
        int restingHR = (session.getUser() != null
                && session.getUser().getRestingHeartRate() != null)
                        ? session.getUser().getRestingHeartRate()
                        : DEFAULT_RESTING_HR;
        double optimalHR = restingHR * OPTIMAL_HR_MULTIPLIER;

        // Arousal Score — Ters-U (Yerkes-Dodson) eğrisi
        double hrDeviation = session.getAvgHeartRate() - optimalHR;
        double arousal = 100.0 * Math.exp(-(hrDeviation * hrDeviation) / AROUSAL_GAUSSIAN_VARIANCE);

        // Stability Score — exponential bozunma
        double variance = session.getHrVariance() != null ? session.getHrVariance() : 0.0;
        double stability = 100.0 * Math.exp(-variance / STABILITY_DECAY);

        // Fizyolojik Skor
        double physio = (arousal * PHYSIO_AROUSAL_WEIGHT) + (stability * PHYSIO_STABILITY_WEIGHT);
        session.setPhysioScore(physio);

        log.debug("Physio: optimalHR={:.1f} avgHR={:.1f} arousal={:.1f} stability={:.1f} physio={:.1f}",
                optimalHR, session.getAvgHeartRate(), arousal, stability, physio);

        // Harmanlama: bilişsel %80 + fizyolojik %20
        return (totalCognitiveScore * BLEND_COGNITIVE) + (physio * BLEND_PHYSIO);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Türetilmiş Metrikler
    // ──────────────────────────────────────────────────────────────────────────

    private DerivedMetrics computeDerivedMetrics(FocusSession session, int sessionDuration) {
        List<Interruption> interruptions = session.getInterruptions() != null
                ? session.getInterruptions()
                : List.of();
        int total = interruptions.size();

        int appSwitchCount = countByType(interruptions, InterruptionType.APP_SWITCH);
        double avgRecoveryTime = computeAvgRecoveryTime(interruptions, total);
        double avgFocusBlock = (double) sessionDuration / (total + 1); // total+1 — sıfıra bölünme yok
        double distractionRate = (double) total / sessionDuration;
        double taskSwitchRate = (double) appSwitchCount / sessionDuration;

        return new DerivedMetrics(appSwitchCount, avgFocusBlock, avgRecoveryTime,
                distractionRate, taskSwitchRate);
    }

    private int countByType(List<Interruption> list, InterruptionType type) {
        return (int) list.stream()
                .filter(i -> type.equals(i.getInterruptionType()))
                .count();
    }

    private double computeAvgRecoveryTime(List<Interruption> list, int total) {
        if (total == 0)
            return 0.0;
        double sum = list.stream()
                .mapToInt(i -> i.getRecoveryTimeSeconds() != null ? i.getRecoveryTimeSeconds() : 0)
                .sum();
        return sum / total;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bilişsel Skor Formülleri
    // ──────────────────────────────────────────────────────────────────────────

    /** 25 × (1 − e^(−avgBlock / 20)) */
    private double computeAttentionScore(double avgFocusBlockMinutes) {
        return WEIGHT_ATTENTION * (1.0 - Math.exp(-avgFocusBlockMinutes / ATTENTION_HALF_SATURATION));
    }

    /**
     * 25 × e^(−distractionRate × (10 × P))
     * P büyüdükçe ceza üssü hızlı büyür → daha düşük skor.
     */
    private double computeDistractionScore(double distractionRate, double penaltyMultiplier) {
        return WEIGHT_DISTRACTION * Math.exp(-distractionRate * (DISTRACTION_DECAY * penaltyMultiplier));
    }

    /**
     * 20 × e^(−taskSwitchRate × (15 × P))
     * STRICT modda uygulama geçişleri çok daha ağır cezalandırılır.
     */
    private double computeSwitchScore(double taskSwitchRate, double penaltyMultiplier) {
        return WEIGHT_SWITCH * Math.exp(-taskSwitchRate * (SWITCH_DECAY * penaltyMultiplier));
    }

    /**
     * 15 × e^(−avgRecovery / (120 / P))
     * P büyüdükçe payda küçülür → tolerans düşer → geri dönüş maliyeti artar.
     * STRICT: eşik 80 sn | DEEP: 120 sn | FLEXIBLE: 240 sn
     */
    private double computeRecoveryScore(double avgRecoveryTimeSeconds, double penaltyMultiplier) {
        return WEIGHT_RECOVERY * Math.exp(-avgRecoveryTimeSeconds / (RECOVERY_DECAY_SECONDS / penaltyMultiplier));
    }

    /** 15 × e^(−(duration−40)² / 800) — 40 dk optimal Gauss eğrisi */
    private double computeQualityScore(int sessionDurationMinutes) {
        double dev = sessionDurationMinutes - QUALITY_OPTIMAL_MINUTES;
        return WEIGHT_QUALITY * Math.exp(-(dev * dev) / QUALITY_GAUSSIAN_VARIANCE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Yardımcılar
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Odak moduna göre ceza çarpanını belirler.
     *
     * STRICT → 1.5 — Sıfır tolerans; aynı kesinti DEEP'e kıyasla %50 daha ağır
     * cezalandırılır.
     * FLEXIBLE → 0.5 — Yüksek tolerans; ceza yarıya düşer, bilinçli molalar
     * normaldir.
     * DEEP/null → 1.0 — Standart referans değeri.
     */
    private double resolvePenaltyMultiplier(FocusMode mode) {
        if (mode == null)
            return 1.0;
        switch (mode) {
            case STRICT:
                return 1.5;
            case FLEXIBLE:
                return 0.5;
            default:
                return 1.0; // DEEP
        }
    }

    private int resolveSessionDuration(FocusSession session) {
        return session.getActualDurationMinutes() != null ? session.getActualDurationMinutes() : 0;
    }

    private void zeroOutAllScores(FocusSession session) {
        session.setAppSwitchCount(0);
        session.setAvgFocusBlockMinutes(0.0);
        session.setAvgRecoveryTimeSeconds(0.0);
        session.setAttentionScore(0.0);
        session.setDistractionScore(0.0);
        session.setSwitchScore(0.0);
        session.setRecoveryScore(0.0);
        session.setQualityScore(0.0);
        session.setPhysioScore(null); // Wearable verisi olmadan sıfır yerine null daha anlamlı
        session.setFinalFocusScore(0.0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // İç Veri Sınıfı — immutable ara metrikler
    // ──────────────────────────────────────────────────────────────────────────

    private record DerivedMetrics(
            int appSwitchCount,
            double avgFocusBlockMinutes,
            double avgRecoveryTimeSeconds,
            double distractionRate,
            double taskSwitchRate) {
    }
}
