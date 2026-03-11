package com.serhatsgr.focusflow_backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Kullanıcının belirli bir güne ait bilişsel analiz sonucunu tutan entity.
 *
 * mentalStateTitle : Kısa başlık ("Odaklı", "Yorgun", "Dağınık" vb.)
 * mentalStateDescription : Uzun açıklama / AI özeti
 *
 * Unique constraint: bir kullanıcının bir günde yalnızca bir DailyAnalysis
 * kaydı olabilir.
 */
@Entity
@Table(name = "daily_analyses", uniqueConstraints = {
        @UniqueConstraint(name = "uq_daily_analysis_user_date", columnNames = { "user_id", "date" })
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
public class DailyAnalysis {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "mental_state_title", length = 100)
    private String mentalStateTitle;

    @Column(name = "mental_state_description", columnDefinition = "TEXT")
    private String mentalStateDescription;
}
