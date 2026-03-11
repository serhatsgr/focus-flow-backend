package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.InterruptionRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionCompleteRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionCompleteResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionStartRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionStartResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionStatusUpdateRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionSummaryResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionTimelineResponseDto;
import com.serhatsgr.focusflow_backend.dto.InterruptionTimelineItemDto;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.entities.Interruption;
import com.serhatsgr.focusflow_backend.entities.InterruptionType;
import com.serhatsgr.focusflow_backend.entities.SessionStatus;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.exception.BaseException;
import com.serhatsgr.focusflow_backend.exception.ErrorMessage;
import com.serhatsgr.focusflow_backend.exception.MessageType;
import com.serhatsgr.focusflow_backend.repository.FocusSessionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Odaklanma oturumu iş mantığı servisi.
 *
 * Güvenlik prensibi:
 * - Kullanıcı kimliği ASLA dışarıdan parametre olarak kabul edilmez.
 * - SecurityContextHolder üzerinden doğrulanmış kullanıcı alınır.
 * - Oturum erişimi DAIMA findByIdAndUserId ile sahiplik doğrulaması yapar.
 */
@Service
public class SessionService {

    private final FocusSessionRepository focusSessionRepository;
    private final FocusScoreCalculatorService focusScoreCalculatorService;
    private final RuleBasedAnalysisService ruleBasedAnalysisService;

    public SessionService(FocusSessionRepository focusSessionRepository,
            FocusScoreCalculatorService focusScoreCalculatorService,
            RuleBasedAnalysisService ruleBasedAnalysisService) {
        this.focusSessionRepository = focusSessionRepository;
        this.focusScoreCalculatorService = focusScoreCalculatorService;
        this.ruleBasedAnalysisService = ruleBasedAnalysisService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Oturum Başlatma
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public SessionStartResponseDto startSession(SessionStartRequestDto request) {
        User currentUser = getAuthenticatedUser();

        if (focusSessionRepository.existsByUserAndStatus(currentUser, SessionStatus.ONGOING)) {
            throw new BaseException(new ErrorMessage(
                    MessageType.BUSINESS_RULE_VIOLATION,
                    "Zaten devam eden bir odaklanma oturumunuz var. " +
                            "Yeni oturum başlatmak için önce mevcut oturumu tamamlayın."));
        }

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        FocusSession session = FocusSession.builder()
                .user(currentUser)
                .startTimeUtc(nowUtc)
                .status(SessionStatus.ONGOING)
                .totalDurationMinutes(0)
                .topic(request.topic())
                .plannedDurationMinutes(request.plannedDurationMinutes())
                .mode(request.mode())
                .build();

        return toStartResponseDto(focusSessionRepository.save(session));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Oturum Duraklatma / Devam Ettirme
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public SessionStartResponseDto updateSessionStatus(UUID sessionId,
            SessionStatusUpdateRequestDto request) {
        User currentUser = getAuthenticatedUser();
        FocusSession session = findSessionForCurrentUser(sessionId, currentUser.getId());

        if (SessionStatus.COMPLETED.equals(session.getStatus())) {
            throw new BaseException(new ErrorMessage(
                    MessageType.BUSINESS_RULE_VIOLATION,
                    "Tamamlanmış bir oturum güncellenemez."));
        }
        if (SessionStatus.COMPLETED.equals(request.status())) {
            throw new BaseException(new ErrorMessage(
                    MessageType.BUSINESS_RULE_VIOLATION,
                    "Oturumu tamamlamak için /complete endpoint'ini kullanın."));
        }

        session.setStatus(request.status());
        return toStartResponseDto(focusSessionRepository.save(session));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Oturum Tamamlama
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public SessionCompleteResponseDto completeSession(UUID sessionId,
            SessionCompleteRequestDto request) {
        User currentUser = getAuthenticatedUser();
        FocusSession session = findSessionForCurrentUser(sessionId, currentUser.getId());

        if (SessionStatus.COMPLETED.equals(session.getStatus())) {
            throw new BaseException(new ErrorMessage(
                    MessageType.BUSINESS_RULE_VIOLATION,
                    "Bu oturum zaten tamamlanmış."));
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setEndTimeUtc(LocalDateTime.now(ZoneOffset.UTC));
        session.setActualDurationMinutes(request.actualDurationMinutes());

        List<Interruption> interruptions = request.interruptions().stream()
                .map(dto -> buildInterruption(dto, session))
                .toList();

        session.getInterruptions().clear();
        session.getInterruptions().addAll(interruptions);

        // ── Giyilebilir Cihaz Verisi Eşleme ──────────────────────────────────
        // calculateAndSetScores() öncesinde yapılmalı: fizyolojik skor hesabı
        // bu verilere ihtiyaç duyar. hasWearableData false/null ise alanlar
        // sıfırlanır ve bilişsel skor %100 ağırlıkla kullanılır.
        if (Boolean.TRUE.equals(request.hasWearableData())) {
            session.setHasWearableData(true);
            session.setAvgHeartRate(request.avgHeartRate());
            session.setHrVariance(request.hrVariance());
        } else {
            session.setHasWearableData(false);
        }

        // ── Kesintilerin toparlanma süresi (Recovery Time) ortalamasını hesapla ──
        if (request.interruptions() != null && !request.interruptions().isEmpty()) {
            double totalRecovery = request.interruptions().stream()
                    .mapToDouble(dto -> dto.recoveryTimeSeconds() != null ? dto.recoveryTimeSeconds() : 0.0)
                    .sum();
            double avgRecovery = totalRecovery / request.interruptions().size();
            session.setAvgRecoveryTimeSeconds(avgRecovery);
        } else {
            session.setAvgRecoveryTimeSeconds(0.0);
        }

        // ── Bilişsel + Fizyolojik skor hesaplama (Faz 2) ─────────────────────
        // calculateAndSetScores() skorları session nesnesine IN-PLACE atar.
        focusScoreCalculatorService.calculateAndSetScores(session);

        // ── Kural Tabanlı Analiz Metni ───────────────────────────────────
        // calculateAndSetScores() bittikten SONRA çağrılır:
        // Analiz, finalFocusScore dahil tüm skorlar zaten session nesnesindeçn
        // bu sayede en güncel veriyle metin üretilir.
        String analysisText = ruleBasedAnalysisService.generateSessionAnalysis(session);
        session.setAiAnalysisResult(analysisText);

        FocusSession saved = focusSessionRepository.save(session);

        return SessionCompleteResponseDto.builder()
                .sessionId(saved.getId())
                .actualDurationMinutes(saved.getActualDurationMinutes())
                .focusScore(saved.getFinalFocusScore() != null
                        ? (int) Math.round(saved.getFinalFocusScore())
                        : null)
                .totalInterruptions(saved.getInterruptions().size())
                .status(saved.getStatus())
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Oturum Özeti (Session Summary)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Tamamlanmış bir oturumun özet bilgisini döner.
     *
     * Güvenlik: findByIdAndUserId ile sahiplik doğrulaması yapılır.
     *
     * Hesaplamalar:
     * - interruptionsCount : Interruption listesinin boyutu
     * - isEarlyExit : actual < planned ise true
     * - focusStability : focusScore'a göre "Yüksek" / "Orta" / "Düşük"
     * - analysisText : aiAnalysisResult (null olabilir, normaldir)
     *
     * @param sessionId Özeti istenen oturumun UUID'si
     * @return SessionSummaryResponseDto
     * @throws BaseException Oturum bulunamazsa veya sahiplik doğrulanamıyorsa
     */
    @Transactional(readOnly = true)
    public SessionSummaryResponseDto getSessionSummary(UUID sessionId) {
        User currentUser = getAuthenticatedUser();
        FocusSession session = findSessionForCurrentUser(sessionId, currentUser.getId());

        int interruptionsCount = session.getInterruptions().size();

        // actualDurationMinutes null koruma — ONGOING oturumda henüz set edilmemiş
        // olabilir
        int actual = session.getActualDurationMinutes() != null ? session.getActualDurationMinutes() : 0;
        int planned = session.getPlannedDurationMinutes() != null ? session.getPlannedDurationMinutes() : 0;
        boolean isEarlyExit = actual < planned;

        int score = session.getFinalFocusScore() != null ? session.getFinalFocusScore().intValue() : 0;
        String focusStability = resolveFocusStability(score);

        return SessionSummaryResponseDto.builder()
                .sessionId(session.getId())
                .actualDurationMinutes(actual)
                .plannedDurationMinutes(planned)
                .interruptionsCount(interruptionsCount)
                .earlyExit(isEarlyExit)
                .focusStability(focusStability)
                .focusScore(score)
                .analysisText(session.getAiAnalysisResult()) // null olabilir — normaldir
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Kesinti Zaman Çizelgesi (Interruption Timeline)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Oturumun kesinti zaman çizelgesini mobil ekran için formatlı döner.
     *
     * @param sessionId Özeti istenen oturumun UUID'si
     * @return SessionTimelineResponseDto (Formatlı zaman ve süre bilgileri içerir)
     */
    @Transactional(readOnly = true)
    public SessionTimelineResponseDto getSessionTimeline(UUID sessionId) {
        User currentUser = getAuthenticatedUser();
        FocusSession session = findSessionForCurrentUser(sessionId, currentUser.getId());

        int score = session.getFinalFocusScore() != null ? session.getFinalFocusScore().intValue() : 0;

        List<InterruptionTimelineItemDto> timelineItems = session.getInterruptions().stream()
                .sorted(Comparator.comparing(Interruption::getTimestampUtc,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(interruption -> InterruptionTimelineItemDto.builder()
                        .time(formatTime(interruption.getTimestampUtc()))
                        .displayTitle(buildDisplayTitle(interruption.getInterruptionType(), interruption.getAppName()))
                        .durationFormatted(formatDuration(interruption.getDurationSeconds()))
                        .build())
                .toList();

        return SessionTimelineResponseDto.builder()
                .focusScore(score)
                .sessionStartTime(formatTime(session.getStartTimeUtc()))
                .sessionEndTime(formatTime(session.getEndTimeUtc()))
                .interruptions(timelineItems)
                .timelineAnalysis(session.getAiAnalysisResult())
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Odak Kararlılığı Metni — focusScore'a göre üç seviye:
     * >= 80 → "Yüksek"
     * 50-79 → "Orta"
     * < 50 → "Düşük"
     */
    private String resolveFocusStability(int focusScore) {
        if (focusScore >= 80)
            return "Yüksek";
        if (focusScore >= 50)
            return "Orta";
        return "Düşük";
    }

    // calculateFocusScore() → FocusScoreCalculatorService.calculateAndSetScores()
    // ile değiştirildi (Faz 2).

    /**
     * InterruptionRequestDto → Interruption entity dönüşümü.
     */
    private Interruption buildInterruption(InterruptionRequestDto dto, FocusSession session) {
        return Interruption.builder()
                .focusSession(session)
                .interruptionType(dto.type())
                .appName(dto.appName())
                .durationSeconds(dto.durationSeconds())
                .recoveryTimeSeconds(dto.recoveryTimeSeconds())
                .build();
    }

    /**
     * Sahiplik doğrulamalı oturum getirme.
     * Oturum yoksa veya başka kullanıcıya aitse 404 döner (bilgi sızdırmaz).
     */
    private FocusSession findSessionForCurrentUser(UUID sessionId, Long userId) {
        return focusSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BaseException(new ErrorMessage(
                        MessageType.RESOURCE_NOT_FOUND,
                        "Oturum bulunamadı veya bu oturuma erişim yetkiniz yok.")));
    }

    /**
     * SecurityContextHolder'dan doğrulanmış User entity'sini alır.
     */
    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalStateException("Güvenlik bağlamında doğrulanmış kullanıcı bulunamadı");
        }
        return user;
    }

    /**
     * FocusSession entity'sini SessionStartResponseDto'ya çevirir.
     */
    private SessionStartResponseDto toStartResponseDto(FocusSession session) {
        return SessionStartResponseDto.builder()
                .sessionId(session.getId())
                .topic(session.getTopic())
                .plannedDurationMinutes(session.getPlannedDurationMinutes())
                .mode(session.getMode())
                .startTimeUtc(session.getStartTimeUtc())
                .status(session.getStatus())
                .build();
    }

    /**
     * UTC zamanını UTC+3'e (TSİ) göre "HH:mm" formatında döndürür.
     */
    private String formatTime(LocalDateTime utcTime) {
        if (utcTime == null)
            return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        // Varsayılan UTC saatine 3 saat ekleyerek basitçe Türkiye saat dilimine
        // ayarlıyoruz.
        // İleride kullanıcı timezone ayarlarına göre (ZoneId belirleyerek)
        // ZonedDateTime kullanılabilir.
        return utcTime.plusHours(3).format(formatter);
    }

    /**
     * Saniye cinsinden süreyi "X sn" veya "X dk Y sn" stringine dönüştürür.
     */
    private String formatDuration(Integer durationSeconds) {
        if (durationSeconds == null)
            return null;
        if (durationSeconds < 60) {
            return durationSeconds + " sn";
        }
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        if (seconds == 0) {
            return minutes + " dk";
        }
        return minutes + " dk " + seconds + " sn";
    }

    /**
     * Enum tipine göre frontend'de gösterilecek okunabilir başlık üretir.
     */
    private String buildDisplayTitle(InterruptionType type, String appName) {
        if (type == null)
            return "Bilinmeyen Kesinti";

        switch (type) {
            case APP_SWITCH:
                return (appName != null && !appName.isBlank()) ? appName + " açıldı" : "Uygulama değişimi";
            case NOTIFICATION:
                return (appName != null && !appName.isBlank()) ? "Bildirim: " + appName : "Bildirim";
            case PHONE_UNLOCK:
                return "Ekran kilidi açıldı";
            case SOCIAL_MEDIA_OPEN:
                return (appName != null && !appName.isBlank()) ? appName + " açıldı" : "Sosyal medya açıldı";
            default:
                return "Kesinti";
        }
    }
}
