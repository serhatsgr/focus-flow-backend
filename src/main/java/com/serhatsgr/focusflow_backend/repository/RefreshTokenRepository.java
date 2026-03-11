package com.serhatsgr.focusflow_backend.repository;

import com.serhatsgr.focusflow_backend.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /**
     * Kullanıcının aktif (kullanılmamış) token'larını getirir.
     * Yeni token üretiminden önce geçerlilik kontrol için kullanılır.
     */
    Optional<RefreshToken> findByUsernameAndUsedFalse(String username);

    /**
     * Süresi dolmuş tüm token'ları temizler (scheduled job için).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    void deleteExpiredTokens(LocalDateTime now);

    /**
     * Kullanıcının tüm token'larını siler (logout / revoke işlemi için).
     */
    @Modifying
    @Transactional
    void deleteByUsername(String username);

    /**
     * Yeni token üretiminden önce kullanıcının tüm eski token'larını
     * "kullanıldı" olarak işaretler.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.used = true WHERE rt.username = :username AND rt.used = false")
    void markAllAsUsedByUsername(String username);
}
