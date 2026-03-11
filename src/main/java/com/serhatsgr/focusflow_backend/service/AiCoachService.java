package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.AiCoachFeedbackDto;
import com.serhatsgr.focusflow_backend.entities.FocusSession;

/**
 * AI Coach servis sözleşmesi (interface).
 *
 * Arayüzün varlık sebebi:
 * - Gerçek LLM implementasyonu (AiCoachServiceImpl) ile mock/test
 * implementasyonları
 * arasında kolayca geçiş yapmayı sağlar.
 * - SessionService bu arayüze bağımlıdır; concrete sınıfa değil.
 */
public interface AiCoachService {

    /**
     * Tamamlanmış bir odaklanma oturumunun verilerini analiz edip
     * yapılandırılmış AI Coach geri bildirimi üretir.
     *
     * @param session Skorları hesaplanmış, tamamlanmış FocusSession entity'si
     * @return AI tarafından üretilen geri bildirim (insights, problems,
     *         suggestions)
     */
    AiCoachFeedbackDto generateSessionFeedback(FocusSession session);
}
