package com.serhatsgr.focusflow_backend.entities;

/**
 * Odaklanma oturumu sırasında yaşanan kesintinin türü.
 * Bilişsel bilim perspektifinden her tür farklı bir dikkat bozulma kategorisini
 * temsil eder.
 *
 * NOTIFICATION : Bildirim geldi (ekrana dokunulmadı, pasif bozulma).
 * APP_SWITCH : Başka bir uygulamaya geçildi (aktif bozulma).
 * PHONE_UNLOCK : Telefon kilidi açıldı (kasıtlı bağlam değişimi).
 * SOCIAL_MEDIA_OPEN : Sosyal medya uygulaması açıldı (yüksek dikkat maliyeti).
 */
public enum InterruptionType {
    NOTIFICATION,
    APP_SWITCH,
    PHONE_UNLOCK,
    SOCIAL_MEDIA_OPEN
}
