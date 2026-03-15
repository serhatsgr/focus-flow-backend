package com.serhatsgr.focusflow_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.UUID;

/**
 * Toplu senkronizasyon yanıtında her bir oturumun sonucunu temsil eder.
 *
 * serverId: Başarılı kayıtta DB'nin atadığı gerçek ID. Flutter bu ID'yi
 * yerel kaydıyla eşleştirerek geçici (localId) kaydını temizler.
 *
 * errorMessage: status="FAILED" olduğunda nedenini açıklar, SUCCESS'te null.
 *
 * @JsonInclude(NON_NULL) ile null alanlar JSON'a dahil edilmez; böylece
 * Flutter tarafı boş field kontrolü yapmak zorunda kalmaz.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncResultItemDto(

        String localId,

        UUID serverId,

        /** "SUCCESS" veya "FAILED" */
        String status,

        String errorMessage

) {
}
