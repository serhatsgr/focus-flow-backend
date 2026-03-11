package com.serhatsgr.focusflow_backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * FocusFlow Refresh Token entity'si.
 * Kullanıcıyla String username üzerinden ilişkilendirilir.
 * isExpired() iş mantığı kapat entity içindedir — zaman karşılaştırması saf
 * veri mantığıdır.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 512)
    private String token;

    /**
     * Kullanıcı adı String olarak tutulur.
     * İleride @ManyToOne User yapısına geçilebilir; şimdilik sade tutuyoruz.
     */
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(nullable = false)
    private boolean used;

    /**
     * Tek kullanılacak constructor: token oluşturulurken kullanılır.
     */
    public RefreshToken(String token, String username, LocalDateTime expiryDate) {
        this.token = token;
        this.username = username;
        this.expiryDate = expiryDate;
        this.createdDate = LocalDateTime.now();
        this.used = false;
    }

    /**
     * Saf veri kontrolü: token'ın süresi dolmuş mu?
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}
