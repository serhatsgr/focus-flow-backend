package com.serhatsgr.focusflow_backend.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.serhatsgr.focusflow_backend.dto.AiCoachFeedbackDto;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.entities.Interruption;
import com.serhatsgr.focusflow_backend.entities.InterruptionType;
import com.serhatsgr.focusflow_backend.exception.BaseException;
import com.serhatsgr.focusflow_backend.exception.ErrorMessage;
import com.serhatsgr.focusflow_backend.exception.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI Coach servisinin OpenAI-uyumlu LLM API üzerinde çalışan implementasyonu.
 *
 * Desteklenen API'ler (OpenAI chat/completions formatı):
 * - OpenAI : https://api.openai.com/v1/chat/completions
 * - DeepSeek : https://api.deepseek.com/chat/completions
 * - Gemini (OpenAI uyumlu uc):
 * https://generativelanguage.googleapis.com/v1beta/openai/
 *
 * application.properties üzerinden model ve URL değiştirilebilir — sınıf
 * değişmez.
 */
@Service
public class AiCoachServiceImpl implements AiCoachService {

    private static final Logger log = LoggerFactory.getLogger(AiCoachServiceImpl.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.coach.api-key}")
    private String apiKey;

    @Value("${ai.coach.model:deepseek-chat}")
    private String model;

    @Value("${ai.coach.api-url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;

    @Value("${ai.coach.max-tokens:800}")
    private int maxTokens;

    public AiCoachServiceImpl(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    // ─── Ana Metot ────────────────────────────────────────────────────────────

    @Override
    public AiCoachFeedbackDto generateSessionFeedback(FocusSession session) {
        String prompt = buildPrompt(session);
        log.debug("AI Coach prompt oluşturuldu — sessionId: {}", session.getId());

        String rawJson = callLlmApi(prompt);
        return parseResponse(rawJson);
    }

    // ─── ADIM 1: Prompt Üretici ───────────────────────────────────────────────

    /**
     * Oturum verilerini yapılandırılmış bir sistem promptuna dönüştürür.
     * LLM'e sadece JSON döndürmesi talimatı verilir — halüsinasyon riski azaltılır.
     */
    private String buildPrompt(FocusSession session) {
        List<Interruption> interruptions = session.getInterruptions() != null
                ? session.getInterruptions()
                : Collections.emptyList();

        String appList = interruptions.stream()
                .filter(i -> i.getAppName() != null && !i.getAppName().isBlank())
                .map(Interruption::getAppName)
                .distinct()
                .collect(Collectors.joining(", "));
        if (appList.isBlank())
            appList = "none";

        long socialMedia = interruptions.stream()
                .filter(i -> InterruptionType.SOCIAL_MEDIA_OPEN.equals(i.getInterruptionType()))
                .count();
        long appSwitches = interruptions.stream()
                .filter(i -> InterruptionType.APP_SWITCH.equals(i.getInterruptionType()))
                .count();

        double finalScore = orZero(session.getFinalFocusScore());
        double attention = orZero(session.getAttentionScore());
        double distraction = orZero(session.getDistractionScore());
        double recovery = orZero(session.getRecoveryScore());
        double quality = orZero(session.getQualityScore());
        double avgRecovery = orZero(session.getAvgRecoveryTimeSeconds());
        double avgBlock = orZero(session.getAvgFocusBlockMinutes());
        int planned = orZeroInt(session.getPlannedDurationMinutes());
        int actual = orZeroInt(session.getActualDurationMinutes());

        return """
                You are an expert productivity and cognitive performance coach.
                Analyze the following focus session data and provide concise, actionable feedback.

                === SESSION DATA ===
                Planned Duration    : %d minutes
                Actual Duration     : %d minutes
                Early Exit          : %s

                === COGNITIVE SCORES (0-100) ===
                Final Focus Score   : %.1f
                Attention Stability : %.1f / 25
                Distraction Resist  : %.1f / 25
                Recovery Efficiency : %.1f / 15
                Session Quality     : %.1f / 15

                === INTERRUPTION DATA ===
                Total Interruptions : %d
                App Switches        : %d
                Social Media Opens  : %d
                Apps Involved       : %s
                Avg Recovery Time   : %.0f seconds
                Avg Focus Block     : %.1f minutes

                === YOUR TASK ===
                Respond with ONLY a valid JSON object. No markdown, no explanation, no extra text.
                The JSON must follow this exact schema:
                {
                  "insights": ["string", "string", "string"],
                  "problems": ["string", "string"],
                  "suggestions": ["string", "string", "string"]
                }
                - insights: 2-3 key observations about the user's focus behavior
                - problems: 1-3 main issues affecting productivity
                - suggestions: 2-4 specific, actionable recommendations
                All strings must be in Turkish language.
                """.formatted(
                planned, actual,
                actual < planned ? "Yes" : "No",
                finalScore, attention, distraction, recovery, quality,
                interruptions.size(), appSwitches, socialMedia, appList,
                avgRecovery, avgBlock);
    }

    // ─── ADIM 2: LLM API Çağrısı ─────────────────────────────────────────────

    /**
     * OpenAI chat/completions formatında LLM API'ye istek gönderir.
     * DeepSeek, OpenAI ve Gemini (OpenAI uyumlu endpoint) ile çalışır.
     *
     * @param prompt Kullanıcı/sistem promptu
     * @return LLM'in ürettiği ham JSON string
     * @throws BaseException API çağrısı başarısız olursa
     */
    private String callLlmApi(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "response_format", Map.of("type", "json_object"), // JSON mod — varsa desteklenir
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "You are a productivity coach. Always respond in valid JSON only."),
                        Map.of("role", "user",
                                "content", prompt)));

        try {
            String responseBody = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return extractContentFromResponse(responseBody);

        } catch (RestClientException e) {
            log.error("AI Coach API isteği başarısız: {}", e.getMessage());
            throw new BaseException(new ErrorMessage(
                    MessageType.INTERNAL_ERROR,
                    "AI Coach servisi şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin."));
        }
    }

    /**
     * OpenAI chat/completions yanıt yapısından içerik metnini çıkarır.
     * Yanıt formatı: choices[0].message.content
     */
    private String extractContentFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root
                    .path("choices").get(0)
                    .path("message")
                    .path("content")
                    .toString().replaceAll("^\"|\"$", ""); // JsonNode string içeriğini güvenle çıkar
        } catch (JacksonException | NullPointerException e) {
            log.error("LLM yanıtı parse edilemedi. Ham yanıt: {}", responseBody);
            throw new BaseException(new ErrorMessage(
                    MessageType.INTERNAL_ERROR,
                    "AI Coach yanıtı işlenirken bir hata oluştu."));
        }
    }

    // ─── ADIM 3: JSON Parse ───────────────────────────────────────────────────

    /**
     * LLM'in döndürdüğü JSON string'ini {@link AiCoachFeedbackDto} nesnesine
     * dönüştürür.
     *
     * <p>
     * Hata yönetimi:
     * </p>
     * <ul>
     * <li>Parse hatası → loglama + güvenli fallback DTO (boş listeler)</li>
     * <li>Eksik alan → Jackson @JsonSetter ile boş liste güvencesi</li>
     * </ul>
     */
    private AiCoachFeedbackDto parseResponse(String jsonContent) {
        try {
            // Olası markdown sarmalayıcıyı temizle (```json ... ```)
            String cleaned = stripMarkdownFences(jsonContent);
            return objectMapper.readValue(cleaned, AiCoachFeedbackDto.class);

        } catch (JacksonException e) {
            log.error("AI Coach JSON parse hatası. İçerik: {}\nHata: {}", jsonContent, e.getMessage());
            // Skor hesaplama başarılıydı ama AI yanıtı parse edilemedi — sistemi çökertme
            return AiCoachFeedbackDto.builder()
                    .insights(List.of("Odaklanma veriniz kaydedildi ancak AI analizi şu an beklenmiyor."))
                    .problems(Collections.emptyList())
                    .suggestions(List.of("Daha iyi analiz için oturumlarınıza devam edin."))
                    .build();
        }
    }

    /**
     * LLM zaman zaman JSON'ı markdown kod bloğu içinde gönderer.
     * Örn: ```json\n{...}\n``` → {...}
     */
    private String stripMarkdownFences(String text) {
        if (text == null)
            return "{}";
        return text.replaceAll("(?s)```(?:json)?\\s*", "").trim();
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private int orZeroInt(Integer value) {
        return value != null ? value : 0;
    }
}
