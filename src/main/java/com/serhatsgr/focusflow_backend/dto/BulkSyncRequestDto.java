package com.serhatsgr.focusflow_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Flutter'ın offline iken biriktirdiği tüm oturumları tek seferde gönderdiği
 * toplu senkronizasyon istek gövdesi.
 *
 * Kısıtlamalar:
 * - En az 1, en fazla 100 oturum gönderilebilir.
 * - Her OfflineSessionDto kendi @Valid kısıtlamalarını taşır.
 */
public record BulkSyncRequestDto(

        @NotEmpty(message = "Senkronize edilecek oturum listesi boş olamaz")
        @Size(max = 100, message = "Tek seferde en fazla 100 oturum senkronize edilebilir")
        @Valid
        List<OfflineSessionDto> sessions

) {
}
