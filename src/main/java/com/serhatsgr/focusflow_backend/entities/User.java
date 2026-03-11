package com.serhatsgr.focusflow_backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * FocusFlow kullanıcı entity'si.
 *
 * TEKNİK NOT — Lombok @Builder.Default + boolean Spring Security çakışması:
 *
 * Sorun: UserDetails arayüzü isEnabled(), isAccountNonExpired() gibi "is"
 * prefix'li
 * metotlar tanımlar. Lombok @Getter boolean alanlarda zaten "is" prefix'i
 * üretir.
 * Bu durum, JPA'nın sütun adını "isEnabled", "isAccountNonLocked" gibi
 * üretmesine
 * yol açar ve DB'deki gerçek sütun adıyla çakışır/uyuşmaz.
 *
 * Çözüm: Her alanda @Column(name = "...") ile açık sütun adı tanımlandı.
 * UserDetails override metotları bizzat yazıldı — Lombok üretmesine izin
 * verilmedi.
 *
 * Güvenlik notu:
 * - password asla plain-text tutulmaz.
 * - Google OAuth kullanıcıları için encode edilmiş rastgele UUID atanır.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = { "password", "authorities" })
public class User implements UserDetails {

    // ─── Kimlik ──────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * BCrypt hash'lenmiş şifre.
     * Google OAuth kullanıcıları için encode edilmiş rastgele UUID atanır.
     */
    @Column(nullable = false)
    private String password;

    // ─── Spring Security Hesap Durumu Bayrakları ──────────────────────────────
    // columnDefinition ile DB-level DEFAULT tanımlandı:
    // - Hibernate ALTER TABLE sırasında mevcut satırlar otomatik default alır.
    // - @Builder.Default primitive boolean için güvenli çalışır.

    @Builder.Default
    @Column(name = "is_enabled", nullable = false, columnDefinition = "boolean NOT NULL DEFAULT true")
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "account_non_expired", nullable = false, columnDefinition = "boolean NOT NULL DEFAULT true")
    private boolean accountNonExpired = true;

    @Builder.Default
    @Column(name = "account_non_locked", nullable = false, columnDefinition = "boolean NOT NULL DEFAULT true")
    private boolean accountNonLocked = true;

    @Builder.Default
    @Column(name = "credentials_non_expired", nullable = false, columnDefinition = "boolean NOT NULL DEFAULT true")
    private boolean credentialsNonExpired = true;

    // ─── Profil Bilgileri ─────────────────────────────────────────────────────

    /**
     * Kimlik doğrulama sağlayıcısı: "LOCAL" veya "GOOGLE".
     */
    @Builder.Default
    @Column(length = 20, nullable = false)
    private String provider = "LOCAL";

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    /**
     * Kullanıcının dinlenik nabzı (bpm).
     * Optimal odak nabzı = restingHeartRate × 1.15 formülünde kullanılır.
     * Varsayılan: 70 bpm (genel yetişkin ortalaması).
     */
    @Builder.Default
    @Column(name = "resting_heart_rate", nullable = false, columnDefinition = "integer NOT NULL DEFAULT 70")
    private Integer restingHeartRate = 70;

    // ─── Roller ──────────────────────────────────────────────────────────────

    @ElementCollection(targetClass = Role.class, fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> authorities = new HashSet<>();

    // ─── UserDetails Override'ları ────────────────────────────────────────────
    // Açıkça override ediliyor — Lombok'un üretmesine bırakılmıyor.
    // Böylece Spring Security, JPA ve Lombok arasındaki naming çakışması önlenir.

    @Override
    public Collection<Role> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}