package com.serhatsgr.focusflow_backend.dto;

import lombok.Builder;

import java.util.List;

/**
 * Toplu senkronizasyon işleminin sonuç özeti.
 *
 * totalReceived   : Gelen toplam oturum sayısı (Flutter'dan gönderilen)
 * successfullySynced : Başarıyla veritabanına yazılan oturum sayısı
 * failedCount     : Hata alan oturum sayısı
 * results         : Her oturum için ayrı ayrı sonuç detayları
 */
@Builder
public record BulkSyncResponseDto(

        int totalReceived,

        int successfullySynced,

        int failedCount,

        List<SyncResultItemDto> results

) {
}
