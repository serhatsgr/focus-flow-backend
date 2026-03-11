package com.serhatsgr.focusflow_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI Coach'un bir oturum analizi sonucunda döndürdüğü yapılandırılmış geri
 * bildirim.
 *
 * insights : Kullanıcının odaklanma davranışıyla ilgili en önemli içgörüler
 * problems : Üretkenliği olumsuz etkileyen tespit edilen sorunlar
 * suggestions : Odaklanmayı artırmak için pratik, uygulanabilir öneriler
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCoachFeedbackDto {

    private List<String> insights;
    private List<String> problems;
    private List<String> suggestions;
}
