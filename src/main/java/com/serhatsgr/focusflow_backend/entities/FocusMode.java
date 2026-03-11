package com.serhatsgr.focusflow_backend.entities;

/**
 * Odaklanma oturum modunu tanımlayan enum.
 *
 * DEEP : Derin odak — bildirimler tamamen kapalı, maksimum konsantrasyon.
 * STRICT : Katı mod — yalnızca kritik bildirimler, görevden sapma engellenir.
 * FLEXIBLE : Esnek mod — kontrollü ara verilmesine izin verir.
 */
public enum FocusMode {
    DEEP,
    STRICT,
    FLEXIBLE
}
