package com.serhatsgr.focusflow_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serhatsgr.focusflow_backend.dto.WeeklyAiResponseDto;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.repository.FocusSessionRepository;
import com.serhatsgr.focusflow_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NEUROCOACH — Haftalık Nöro-Verimlilik Analiz Motoru.
 *
 * Bu servis kullanıcının 7 günlük odaklanma verilerini toplayıp zenginleştirdikten
 * sonra LLM'e (Gemini) gönderir ve bilimsel tabanlı koçluk raporu üretir.
 *
 * Veri Akışı:
 * DB (FocusSession + Interruptions) → aggregateWeeklyData() → LLM Prompt → parseResponse() → DTO
 */
@Service
public class WeeklyAiCoachService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyAiCoachService.class);
    private static final int MAX_RETRY_ATTEMPTS = 1;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FocusSessionRepository focusSessionRepository;
    private final UserRepository userRepository;
    private final DailyRuleBasedAnalysisService dailyRuleBasedAnalysisService;

    @Value("${ai.coach.api-url}")
    private String apiUrl;

    @Value("${ai.coach.api-key}")
    private String apiKey;

    @Value("${ai.coach.model}")
    private String apiModel;

    @Value("${ai.coach.max-tokens:2000}")
    private int maxTokens;

    public WeeklyAiCoachService(ObjectMapper objectMapper,
            FocusSessionRepository focusSessionRepository,
            UserRepository userRepository,
            DailyRuleBasedAnalysisService dailyRuleBasedAnalysisService) {
        this.objectMapper = objectMapper;
        this.focusSessionRepository = focusSessionRepository;
        this.userRepository = userRepository;
        this.dailyRuleBasedAnalysisService = dailyRuleBasedAnalysisService;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(45000);
        requestFactory.setConnectTimeout(10000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // SYSTEM PROMPT — NEUROCOACH Personası
    // ══════════════════════════════════════════════════════════════════════════════

    private static final String SYSTEM_PROMPT = """
            You are "NEUROCOACH" — an elite, brutally honest cognitive performance coach with expertise in neuroscience (Andrew Huberman's scientific rigor), relentless discipline (David Goggins' mentality), and behavioral psychology.

            ## YOUR PERSONALITY
            - You are DIRECT, INTENSE, and SCIENTIFIC. No sugarcoating.
            - You back every claim with neuroscience (prefrontal cortex, dopamine, cortisol, circadian rhythm, context-switching cost).
            - You celebrate genuine wins HARD but expose mediocrity without mercy.
            - You speak in short, punchy, powerful sentences. Like a coach mid-game.
            - Your language is Turkish (Türkçe). All output MUST be in Turkish.

            ## YOUR TASK
            You will receive the user's 7-day focus session data as a JSON object with two parts:
            1. "weekSummary": Overall week totals (totalSessions, totalDurationMinutes, overallAverageScore, totalInterruptions, totalAppSwitches, activeDays)
            2. "dailyBreakdown": Array of daily data, each containing:
               - day, sessionCount, totalDurationMinutes, averageScore, totalInterruptions
               - avgAttentionScore, avgDistractionScore, avgSwitchScore, avgRecoveryScore
               - totalAppSwitches, ruleBasedLabel

            Analyze this data and produce a weekly performance report.

            ## OUTPUT RULES (STRICT)
            You MUST respond with a single valid JSON object. No markdown, no explanation, no text outside JSON.

            The JSON object MUST contain EXACTLY these fields:
            {
              "weeklyTheme": "1-3 kelimelik güçlü rozet (Örn: 'Prefrontal Fırtına', 'Dopamin Krizi', 'Çelik İrade', 'Bilişsel Çöküş')",
              "weeklySummary": "3-5 cümlelik agresif, bilimsel genel hafta analizi. Gün gün referans ver. Nörolojik terimleri (kortizol, amigdala, prefrontal yorgunluk, sirkadiyen ritim) aktif kullan.",
              "strengths": ["En az 1, en fazla 3 güçlü yön. Her biri 1 cümle. Spesifik gün ve metrik referansı ile."],
              "weaknesses": ["En az 1, en fazla 3 zayıf yön. Her biri 1 cümle. Acımasızca. Hangi günde ne oldu?"],
              "actionableAdvice": "TEK, çok spesifik, bilimsel eylem planı. Hangi gün, hangi saatte, ne yapılacak. Dopamin/kortizol/nöroplastisite referansı ile. 2-3 cümle."
            }

            ## CRITICAL CONSTRAINTS
            - DO NOT wrap your response in markdown code blocks.
            - DO NOT add any text before or after the JSON.
            - "weeklyTheme" must be 1-3 words maximum.
            - "strengths" and "weaknesses" are JSON arrays of strings.
            - "actionableAdvice" is a single string, NOT an array.
            - If user had zero sessions, return theme="Sessiz Hafta" and adjust all fields accordingly.
            - ALWAYS reference specific days from the data (e.g., "Çarşamba günü...").
            """;

    // ══════════════════════════════════════════════════════════════════════════════
    // ANA METOD
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Verilen referenceDate'den geriye dönük 7 günlük verileri analiz ederek
     * Haftalık Nöro-Verimlilik geri bildirimi üretir.
     */
    public WeeklyAiResponseDto generateWeeklyAnalysis(String email, LocalDate referenceDate) {
        User user;
        try {
            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + email));
        } catch (IllegalArgumentException e) {
            log.error("Kullanıcı bulunamadı: {}", email);
            return getFallbackResponse(false);
        }

        LocalDateTime endOfRange = referenceDate.atTime(23, 59, 59);
        LocalDateTime startOfRange = referenceDate.minusDays(7).atStartOfDay();

        // 1. Veri çekimi
        List<FocusSession> sessions = focusSessionRepository.findByUserIdAndStartTimeUtcBetweenFetchInterruptions(
                user.getId(), startOfRange, endOfRange);

        // 2. Zenginleştirilmiş Veri Agregasyonu
        String promptData = aggregateWeeklyData(sessions);

        log.info("NEUROCOACH — Veri hazırlandı [user={}, sessions={}, range={}/{}]",
                user.getId(), sessions.size(), startOfRange.toLocalDate(), endOfRange.toLocalDate());
        log.debug("NEUROCOACH — Agregasyon çıktısı: {}", promptData);

        // 3. LLM Çağrısı (retry ile)
        return callLlmWithRetry(promptData, sessions.isEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // VERİ AGREGASYONU — Zenginleştirilmiş
    // ══════════════════════════════════════════════════════════════════════════════

    private String aggregateWeeklyData(List<FocusSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "{\"weekSummary\":{\"totalSessions\":0,\"activeDays\":0},\"dailyBreakdown\":[]}";
        }

        // ── Günlere göre gruplama ──
        Map<DayOfWeek, List<FocusSession>> sessionsByDay = sessions.stream()
                .filter(s -> s.getStartTimeUtc() != null)
                .collect(Collectors
                        .groupingBy(s -> s.getStartTimeUtc().atZone(ZoneOffset.UTC).toLocalDate().getDayOfWeek()));

        List<Map<String, Object>> dailyDataList = new ArrayList<>();

        // ── Hafta toplam akümülatörleri ──
        int weekTotalDuration = 0;
        int weekTotalInterruptions = 0;
        int weekTotalAppSwitches = 0;
        int weekTotalSessions = 0;
        double weekScoreSum = 0;
        int weekScoreCount = 0;

        for (DayOfWeek day : DayOfWeek.values()) {
            List<FocusSession> daySessions = sessionsByDay.getOrDefault(day, Collections.emptyList());
            if (daySessions.isEmpty()) {
                continue;
            }

            int totalDuration = daySessions.stream()
                    .mapToInt(s -> s.getActualDurationMinutes() != null ? s.getActualDurationMinutes()
                            : s.getTotalDurationMinutes())
                    .sum();

            double averageScore = daySessions.stream()
                    .filter(s -> s.getFinalFocusScore() != null)
                    .mapToDouble(FocusSession::getFinalFocusScore)
                    .average()
                    .orElse(0.0);

            int totalInterruptions = daySessions.stream()
                    .mapToInt(s -> s.getInterruptions() != null ? s.getInterruptions().size() : 0)
                    .sum();

            // ── Bilişsel Skor Ortalamaları ──
            double avgAttention = safeAverage(daySessions, FocusSession::getAttentionScore);
            double avgDistraction = safeAverage(daySessions, FocusSession::getDistractionScore);
            double avgSwitch = safeAverage(daySessions, FocusSession::getSwitchScore);
            double avgRecovery = safeAverage(daySessions, FocusSession::getRecoveryScore);

            int totalAppSwitches = daySessions.stream()
                    .mapToInt(s -> s.getAppSwitchCount() != null ? s.getAppSwitchCount() : 0)
                    .sum();

            // ── Rule-Based Label ──
            long seed = day.getValue() * 10L + totalDuration;
            String ruleBasedLabel = dailyRuleBasedAnalysisService
                    .getGeneralStatus(averageScore, totalInterruptions, seed).title();

            // ── Günlük Veri Map ──
            Map<String, Object> daySummary = new LinkedHashMap<>();
            daySummary.put("day", day.name());
            daySummary.put("sessionCount", daySessions.size());
            daySummary.put("totalDurationMinutes", totalDuration);
            daySummary.put("averageScore", Math.round(averageScore));
            daySummary.put("totalInterruptions", totalInterruptions);
            daySummary.put("avgAttentionScore", Math.round(avgAttention));
            daySummary.put("avgDistractionScore", Math.round(avgDistraction));
            daySummary.put("avgSwitchScore", Math.round(avgSwitch));
            daySummary.put("avgRecoveryScore", Math.round(avgRecovery));
            daySummary.put("totalAppSwitches", totalAppSwitches);
            daySummary.put("ruleBasedLabel", ruleBasedLabel);

            dailyDataList.add(daySummary);

            // ── Hafta toplam akümülatörleri güncelle ──
            weekTotalDuration += totalDuration;
            weekTotalInterruptions += totalInterruptions;
            weekTotalAppSwitches += totalAppSwitches;
            weekTotalSessions += daySessions.size();
            if (averageScore > 0) {
                weekScoreSum += averageScore;
                weekScoreCount++;
            }
        }

        // ── Hafta Toplamı ──
        Map<String, Object> weekSummary = new LinkedHashMap<>();
        weekSummary.put("totalSessions", weekTotalSessions);
        weekSummary.put("totalDurationMinutes", weekTotalDuration);
        weekSummary.put("overallAverageScore", weekScoreCount > 0 ? Math.round(weekScoreSum / weekScoreCount) : 0);
        weekSummary.put("totalInterruptions", weekTotalInterruptions);
        weekSummary.put("totalAppSwitches", weekTotalAppSwitches);
        weekSummary.put("activeDays", dailyDataList.size());

        // ── Final JSON ──
        Map<String, Object> fullPayload = new LinkedHashMap<>();
        fullPayload.put("weekSummary", weekSummary);
        fullPayload.put("dailyBreakdown", dailyDataList);

        try {
            return objectMapper.writeValueAsString(fullPayload);
        } catch (Exception e) {
            log.warn("Haftalık veriyi JSON'a çevirirken hata oluştu. Düz metin dönecek.", e);
            return fullPayload.toString();
        }
    }

    /**
     * Null-safe ortalama hesaplama. Belirli bir Double alanı için.
     */
    private double safeAverage(List<FocusSession> sessions,
            java.util.function.Function<FocusSession, Double> getter) {
        return sessions.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // LLM ÇAĞRISI — Retry Mekanizmalı
    // ══════════════════════════════════════════════════════════════════════════════

    private WeeklyAiResponseDto callLlmWithRetry(String promptData, boolean hasNoSessions) {
        String jsonPayload = buildRequestBody(promptData);

        for (int attempt = 0; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String responseStr = restClient.post()
                        .uri(apiUrl)
                        .header("Authorization", "Bearer " + apiKey.trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonPayload)
                        .retrieve()
                        .body(String.class);

                return parseResponse(responseStr);

            } catch (RestClientException e) {
                log.warn("NEUROCOACH — LLM API isteği başarısız (deneme {}/{}): {}",
                        attempt + 1, MAX_RETRY_ATTEMPTS + 1, e.getMessage());
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    log.error("NEUROCOACH — Tüm LLM denemeleri başarısız oldu.", e);
                    return getFallbackResponse(!hasNoSessions);
                }
                // Retry öncesi kısa bekleme
                sleep(2000);
            } catch (Exception e) {
                log.error("NEUROCOACH — JSON parse veya beklenmeyen hata (deneme {}/{}): ",
                        attempt + 1, MAX_RETRY_ATTEMPTS + 1, e);
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    return getFallbackResponse(!hasNoSessions);
                }
                sleep(1000);
            }
        }

        return getFallbackResponse(!hasNoSessions);
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // REQUEST BODY
    // ══════════════════════════════════════════════════════════════════════════════

    private String buildRequestBody(String userData) {
        try {
            Map<String, Object> systemMessage = Map.of(
                    "role", "system",
                    "content", SYSTEM_PROMPT);

            Map<String, Object> userMessage = Map.of(
                    "role", "user",
                    "content", "İşte 7 günlük odaklanma verim:\n" + userData);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", apiModel);
            requestBody.put("messages", List.of(systemMessage, userMessage));
            requestBody.put("max_tokens", maxTokens);

            return objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("LLM istek gövdesi oluşturulamadı.", e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // YANIT PARSE
    // ══════════════════════════════════════════════════════════════════════════════

    private WeeklyAiResponseDto parseResponse(String rawResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawResponse);

            // OpenAI/Gemini formatından content'i çıkarma
            String content = rootNode.path("choices").path(0).path("message").path("content").asText();

            // Markdown tag temizleme (LLM bazen ```json wrapper ekleyebilir)
            String cleanJson = content
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode contentNode = objectMapper.readTree(cleanJson);

            String theme = contentNode.path("weeklyTheme").asText("Bilinmeyen Tema");
            String summary = contentNode.path("weeklySummary").asText("-");
            String advice = contentNode.path("actionableAdvice").asText("-");

            // strengths ve weaknesses — JSON array → List<String>
            List<String> strengths = jsonArrayToList(contentNode.path("strengths"));
            List<String> weaknesses = jsonArrayToList(contentNode.path("weaknesses"));

            return WeeklyAiResponseDto.builder()
                    .weeklyTheme(theme)
                    .weeklySummary(summary)
                    .strengths(strengths)
                    .weaknesses(weaknesses)
                    .actionableAdvice(advice)
                    .build();
        } catch (Exception e) {
            log.error("NEUROCOACH — LLM yanıtı parse edilemedi: {}", rawResponse, e);
            throw new RuntimeException("JSON parse hatası", e);
        }
    }

    /**
     * JsonNode dizisini List<String>'e dönüştürür.
     * Node yoksa veya dizi değilse boş liste döner.
     */
    private List<String> jsonArrayToList(JsonNode arrayNode) {
        if (arrayNode == null || arrayNode.isMissingNode() || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode element : arrayNode) {
            result.add(element.asText());
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // FALLBACK — Bağlama Duyarlı
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * LLM yanıt veremediğinde döndürülecek yedek yanıt.
     *
     * @param hasData Kullanıcının hafta içinde seans verisi olup olmadığı
     */
    private WeeklyAiResponseDto getFallbackResponse(boolean hasData) {
        if (hasData) {
            return WeeklyAiResponseDto.builder()
                    .weeklyTheme("Analiz Beklemede")
                    .weeklySummary(
                            "Haftalık verilerini topladık ancak yapay zeka motorumuz şu an yoğunluk yaşıyor. " +
                                    "Verilerin güvende — kısa süre sonra tekrar dene, analizin hazır olacak.")
                    .strengths(List.of("Bu hafta oturum kaydın var — bu bile büyük bir adım."))
                    .weaknesses(List.of("Analiz motoru geçici olarak devre dışı, detaylı zayıf yön tespiti yapılamadı."))
                    .actionableAdvice(
                            "Prefrontal korteksinin toparlanması için kesintilerden arınmış, net sınırları olan " +
                                    "en az bir tane hiper-odak seansı planla. Birkaç dakika sonra analizi tekrar iste.")
                    .build();
        }

        return WeeklyAiResponseDto.builder()
                .weeklyTheme("Sessiz Hafta")
                .weeklySummary(
                        "Bu hafta odaklanma oturumu kaydedilmedi. Sirkadiyen ritmini ve bilişsel alışkanlıklarını " +
                                "analiz edebilmemiz için en az birkaç seansa ihtiyacımız var.")
                .strengths(List.of("Uygulamayı açtın ve analizine baktın — bu farkındalık güçlü bir başlangıç."))
                .weaknesses(List.of("Sıfır seans kaydı. Zihinsel kas antrenman yapılmadan güçlenmez."))
                .actionableAdvice(
                        "Yarın sabah ilk iş olarak 25 dakikalık tek bir odak bloğu başlat. " +
                                "Dopamin döngüsünü tetiklemek için küçük başlamak, hiç başlamamaktan katbekat iyidir.")
                .build();
    }
}
