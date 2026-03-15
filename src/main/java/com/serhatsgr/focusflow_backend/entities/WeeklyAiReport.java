package com.serhatsgr.focusflow_backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Haftalık AI Rapor Entity'si.
 *
 * Pazar gecesi scheduler tarafından üretilir ve DB'de saklanır.
 * Kullanıcı rapor istediğinde önce buradan okunur (sıfır LLM maliyeti).
 *
 * Unique constraint: Bir kullanıcı için bir hafta başlangıç tarihinde
 * yalnızca tek rapor olabilir.
 */
@Entity
@Table(name = "weekly_ai_reports", uniqueConstraints = {
        @UniqueConstraint(name = "uk_weekly_report_user_week",
                columnNames = { "user_id", "week_start_date" })
}, indexes = {
        @Index(name = "idx_weekly_report_user_week",
                columnList = "user_id, week_start_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
public class WeeklyAiReport {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ─── Hafta Aralığı ───────────────────────────────────────────────────────

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    // ─── NEUROCOACH Rapor İçeriği ────────────────────────────────────────────

    @Column(name = "theme", nullable = false, length = 100)
    private String theme;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "actionable_advice", nullable = false, columnDefinition = "TEXT")
    private String actionableAdvice;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "weekly_ai_report_strengths",
            joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "strength", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "weekly_ai_report_weaknesses",
            joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "weakness", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> weaknesses = new ArrayList<>();

    // ─── Zaman Damgası ───────────────────────────────────────────────────────

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
