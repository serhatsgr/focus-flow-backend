package com.serhatsgr.focusflow_backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bir kullanıcı odaklanma oturumunu temsil eden entity.
 *
 * Faz 1 güncellemesi — AI Coach Analitik Altyapısı:
 *
 * Türetilmiş Metrikler (AI analizine ham girdi):
 * appSwitchCount : Uygulama geçişi sayısı
 * avgFocusBlockMinutes : Ortalama kesintisiz odaklanma bloğu süresi
 * avgRecoveryTimeSeconds : Ortalama bilişsel geri dönüş süresi
 *
 * Bilişsel Skor Bileşenleri (0-100):
 * attentionScore : Dikkat sürekliliği
 * distractionScore: Dikkat dağınıklığı direnci
 * switchScore : Bağlam değişim maliyeti
 * recoveryScore : Bilişsel esneklik / geri dönüş hızı
 * qualityScore : Genel oturum kalitesi
 *
 * Final Skor:
 * finalFocusScore (Double) — focusScore (Integer) yerine geçer
 */
@Entity
@Table(name = "focus_sessions", indexes = {
        @Index(name = "idx_focus_session_user_start", columnList = "user_id, start_time_utc")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = { "user", "interruptions" })
public class FocusSession {

    // ─── Kimlik ──────────────────────────────────────────────────────────────

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ─── Zaman Bilgileri ─────────────────────────────────────────────────────

    @Column(name = "start_time_utc", nullable = false)
    private LocalDateTime startTimeUtc;

    @Column(name = "end_time_utc")
    private LocalDateTime endTimeUtc;

    // ─── Oturum Durumu ───────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    // ─── Pre-Session Bilgileri ────────────────────────────────────────────────

    @Column(name = "topic", length = 255)
    private String topic;

    @Column(name = "planned_duration_minutes", nullable = false)
    private Integer plannedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private FocusMode mode;

    // ─── Post-Session Temel Bilgileri ─────────────────────────────────────────

    /** Frontend'den gelen gerçek odaklanma süresi (dakika). */
    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    /**
     * Geriye dönük uyumluluk için korundu — yeni hesaplamalar finalFocusScore
     * kullanır.
     */
    @Builder.Default
    @Column(name = "total_duration_minutes", nullable = false)
    private Integer totalDurationMinutes = 0;

    /**
     * AI Coach tarafından üretilen oturum analizi metni. Henüz üretilmediyse null.
     */
    @Column(name = "ai_analysis_result", columnDefinition = "TEXT")
    private String aiAnalysisResult;

    // ─── Türetilmiş Metrikler (AI Analizi Ham Girdisi) ────────────────────────

    /**
     * Oturum boyunca gerçekleşen uygulama geçişi (APP_SWITCH) sayısı.
     * switchScore hesaplamasının ana girdisi.
     */
    @Builder.Default
    @Column(name = "app_switch_count", nullable = false)
    private Integer appSwitchCount = 0;

    /**
     * Kesintisiz geçen ortalama odaklanma bloğu süresi (dakika).
     * Yüksek değer → uzun, sağlıklı odaklanma aralıkları.
     * attentionScore hesaplamasının ana girdisi.
     */
    @Builder.Default
    @Column(name = "avg_focus_block_minutes", nullable = false)
    private Double avgFocusBlockMinutes = 0.0;

    /**
     * Ortalama bilişsel geri dönüş süresi (saniye).
     * Kesintiden sonra tam odaklanmaya geçiş için geçen süre.
     * recoveryScore hesaplamasının ana girdisi.
     */
    @Builder.Default
    @Column(name = "avg_recovery_time_seconds", nullable = false)
    private Double avgRecoveryTimeSeconds = 0.0;

    // ─── Bilişsel Skor Bileşenleri (0-100) ────────────────────────────────────

    /**
     * Dikkat sürekliliği skoru — kesintiler arasındaki ortalama odak bloğundan
     * türetilir.
     */
    @Column(name = "attention_score")
    private Double attentionScore;

    /**
     * Dikkat dağınıklığı direnci skoru — kesintilere verilerin yoğunluğundan
     * türetilir.
     */
    @Column(name = "distraction_score")
    private Double distractionScore;

    /** Bağlam değişim maliyeti skoru — appSwitchCount'tan türetilir. */
    @Column(name = "switch_score")
    private Double switchScore;

    /**
     * Bilişsel esneklik / geri dönüş hızı skoru — avgRecoveryTimeSeconds'dan
     * türetilir.
     */
    @Column(name = "recovery_score")
    private Double recoveryScore;

    /**
     * Genel oturum kalitesi skoru — gerçek/planlanan süre ve mod uyumundan
     * türetilir.
     */
    @Column(name = "quality_score")
    private Double qualityScore;

    // ─── Biyofeedback (Giyilebilir Cihaz) Alanları ───────────────────────────

    /**
     * Bu oturumda giyilebilir cihazdan veri alındı mı?
     * false ise avgHeartRate, hrVariance ve physioScore null kalır;
     * finalFocusScore tamamen bilişsel skor olarak hesaplanır.
     */
    @Builder.Default
    @Column(name = "has_wearable_data", nullable = false)
    private Boolean hasWearableData = false;

    /**
     * Oturumun ortalama kalp atış hızı (bpm).
     * Fizyolojik Arousal Score hesaplamasının girdisi.
     */
    @Column(name = "avg_heart_rate")
    private Double avgHeartRate;

    /**
     * Kalp atış hızı varyansı (bpm²).
     * Düşük varyans → sakin/odaklanmış. Yüksek varyans → stres/dağınık.
     * Fizyolojik Stability Score hesaplamasının girdisi.
     */
    @Column(name = "hr_variance")
    private Double hrVariance;

    /**
     * Kalp verisinden elde edilen fizyolojik skor (0–100).
     * Ters-U arousal eğrisi × stabilitity ağırlıklı ortalaması.
     * hasWearableData = false ise null kalır.
     */
    @Column(name = "physio_score")
    private Double physioScore;

    // ─── Final Skor ──────────────────────────────────────────────────────────

    /**
     * Harmanlanmış final skor (0.0–100.0).
     * hasWearableData = true → (bilişsel × 0.80) + (fizyolojik × 0.20)
     * hasWearableData = false → bilişsel skor (100%)
     */
    @Column(name = "final_focus_score")
    private Double finalFocusScore;

    // ─── İlişkiler ───────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "focusSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Interruption> interruptions = new ArrayList<>();
}
