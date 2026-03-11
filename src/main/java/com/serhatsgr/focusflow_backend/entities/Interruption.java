package com.serhatsgr.focusflow_backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Odaklanma oturumu sırasında yaşanan bir kesinti olayı.
 *
 * Yeni bilimsel metrik alanları:
 * - timestampUtc : Kesintinin yaşandığı UTC anı
 * - recoveryTimeSeconds: Kullanıcının odaklanmaya geri dönme süresi (saniye)
 * Dünya literatüründe ortalama ~23 dakika (Gloria Mark, 2005).
 * Bu veri AI Coach skorlamasına beslenir.
 */
@Entity
@Table(name = "interruptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "focusSession")
public class Interruption {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "focus_session_id", nullable = false)
    private FocusSession focusSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "interruption_type", nullable = false, length = 30)
    private InterruptionType interruptionType;

    /**
     * Kesintiye neden olan uygulama adı. APP_SWITCH / SOCIAL_MEDIA_OPEN için
     * anlamlı.
     */
    @Column(name = "app_name", length = 100)
    private String appName;

    /** Kesintinin toplam süresi (saniye). */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /**
     * Kesintinin yaşandığı UTC anı.
     * Oturum içindeki kesinti zaman dağılımını analiz etmek için kullanılır.
     */
    @Column(name = "timestamp_utc")
    private LocalDateTime timestampUtc;

    /**
     * Kullanıcının kesintiden sonra tam odaklanmaya geri dönmesinin süresi
     * (saniye).
     * Bilişsel geri dönüş maliyeti (Cognitive Recovery Cost) olarak bilinir.
     * AI Coach, bu metriği recoveryScore hesaplamasında kullanır.
     * Henüz ölçülmediyse null olabilir.
     */
    @Column(name = "recovery_time_seconds")
    private Integer recoveryTimeSeconds;
}
