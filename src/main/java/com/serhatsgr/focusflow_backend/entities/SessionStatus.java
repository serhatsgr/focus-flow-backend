package com.serhatsgr.focusflow_backend.entities;

/**
 * Bir odaklanma oturumunun anlık durumu.
 *
 * ONGOING : Oturum devam ediyor.
 * PAUSED : Oturum kullanıcı tarafından duraklatıldı.
 * COMPLETED : Oturum tamamlandı.
 */
public enum SessionStatus {
    ONGOING,
    PAUSED,
    COMPLETED
}
