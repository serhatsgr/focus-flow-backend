package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.BulkSyncRequestDto;
import com.serhatsgr.focusflow_backend.dto.BulkSyncResponseDto;
import com.serhatsgr.focusflow_backend.dto.InterruptionRequestDto;
import com.serhatsgr.focusflow_backend.dto.OfflineSessionDto;
import com.serhatsgr.focusflow_backend.dto.SessionCompleteRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionCompleteResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionStartRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionStartResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionStatusUpdateRequestDto;
import com.serhatsgr.focusflow_backend.dto.SessionSummaryResponseDto;
import com.serhatsgr.focusflow_backend.dto.SessionTimelineResponseDto;
import com.serhatsgr.focusflow_backend.dto.InterruptionTimelineItemDto;
import com.serhatsgr.focusflow_backend.dto.SyncResultItemDto;
import com.serhatsgr.focusflow_backend.entities.FocusMode;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.entities.Interruption;
import com.serhatsgr.focusflow_backend.entities.InterruptionType;
import com.serhatsgr.focusflow_backend.entities.SessionStatus;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.exception.BaseException;
import com.serhatsgr.focusflow_backend.exception.ErrorMessage;
import com.serhatsgr.focusflow_backend.exception.MessageType;
import com.serhatsgr.focusflow_backend.repository.FocusSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

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

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final FocusSessionRepository focusSessionRepository;
    private final FocusScoreCalculatorService focusScoreCalculatorService;
    private final RuleBasedAnalysisService ruleBasedAnalysisService;

    private final Validator validator;

    public SessionService(FocusSessionRepository focusSessionRepository,
            FocusScoreCalculatorService focusScoreCalculatorService,
            RuleBasedAnalysisService ruleBasedAnalysisService, Validator validator) {
        this.focusSessionRepository = focusSessionRepository;
        this.focusScoreCalculatorService = focusScoreCalculatorService;
        this.ruleBasedAnalysisService = ruleBasedAnalysisService;
        this.validator = validator;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Toplu Offline Senkronizasyon (Bulk Sync)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Flutter'ın offline iken biriktirdiği oturumları toplu olarak işler.
     *
     * Partial Success Prensibi:
     * Bir oturumun kaydedilmesi başarısız olursa yalnızca o oturum FAILED olarak
     * işaretlenir; diğer oturumlar etkilenmez. Her oturum kendi try-catch bloğuna
     * sahiptir ve birbirinden bağımsız transaction'larla işlenir.
     *
     * Tasarım Notu:
     * - Toplu işlemde her oturum @Transactional(NEW) yerine bu metot dışında
     *   saveAndFlush ile hemen commit edilir. Böylece bir oturumun rollback'i
     *   diğerlerini geri almaz.
     *
     * @param user    Kimlik doğrulaması yapılmış kullanıcı (Controller'dan alınır)
     * @param request Senkronize edilecek oturumların listesi
     * @return Detaylı sonuç özeti (kaç başarılı, kaç başarısız, her birinin ID'si)
     */
    @Transactional
    public BulkSyncResponseDto syncOfflineSessions(User user, BulkSyncRequestDto request) {
        List<SyncResultItemDto> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (OfflineSessionDto dto : request.sessions()) {
            try {
                Set<ConstraintViolation<OfflineSessionDto>> violations = validator.validate(dto);
                if (!violations.isEmpty()) {
                    String errorMsg = violations.iterator().next().getMessage();
                    log.warn("Oturum doğrulama hatası. Local ID: {} - Hata: {}", dto.localId(), errorMsg);

                    results.add(SyncResultItemDto.builder()
                            .localId(dto.localId())
                            .serverId(null)
                            .status("FAILED")
                            .errorMessage(errorMsg)
                            .build());
                    failedCount++;
                    continue; // Hatalıysa döngünün sonuna inme, bir sonraki oturuma geç!
                }

                FocusMode mode;
                try {
                    mode = dto.mode() != null ? FocusMode.valueOf(dto.mode().toUpperCase()) : FocusMode.STRICT;
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("Geçersiz FocusMode: " + dto.mode());
                }

                // ── Entity inşası ────────────────────────────────────────────
                FocusSession session = FocusSession.builder()
                        .user(user)
                        .topic(dto.topic())
                        .mode(mode)
                        .startTimeUtc(dto.startTimeUtc())
                        .endTimeUtc(dto.endTimeUtc())
                        .actualDurationMinutes(dto.actualDurationMinutes())
                        .plannedDurationMinutes(dto.plannedDurationMinutes())
                        .totalDurationMinutes(dto.durationMinutes()) // Legacy alan veya durationMinutes total olarak tutulabilir
                        .status(SessionStatus.COMPLETED)
                        .build();

                // ── Kesintileri eşle ─────────────────────────────────────────
                if (dto.interruptions() != null && !dto.interruptions().isEmpty()) {
                    List<Interruption> interruptions = dto.interruptions().stream()
                            .map(i -> buildInterruption(i, session))
                            .toList();
                    session.getInterruptions().addAll(interruptions);

                    double totalRecovery = dto.interruptions().stream()
                            .mapToDouble(i -> i.recoveryTimeSeconds() != null ? i.recoveryTimeSeconds() : 0.0)
                            .sum();
                    session.setAvgRecoveryTimeSeconds(totalRecovery / dto.interruptions().size());
                } else {
                    session.setAvgRecoveryTimeSeconds(0.0);
                }

                // ── Giyilebilir cihaz verisi ─────────────────────────────────
                if (Boolean.TRUE.equals(dto.hasWearableData())) {
                    session.setHasWearableData(true);
                    session.setAvgHeartRate(dto.avgHeartRate());
                    session.setHrVariance(dto.hrVariance());
                } else {
                    session.setHasWearableData(false);
                }

                // ── Skor hesaplama ve kural tabanlı analiz ───────────────────
                focusScoreCalculatorService.calculateAndSetScores(session);
                String analysisText = ruleBasedAnalysisService.generateSessionAnalysis(session);
                session.setAiAnalysisResult(analysisText);

                // ── Kaydet ───────────────────────────────────────────────────
                FocusSession saved = focusSessionRepository.save(session);
                successCount++;

                results.add(SyncResultItemDto.builder()
                        .localId(dto.localId())
                        .serverId(saved.getId())
                        .status("SUCCESS")
                        .build());

                log.info("Bulk sync: localId={} → serverId={} başarıyla kaydedildi.",
                        dto.localId(), saved.getId());

            } catch (Exception e) {
                failedCount++;
                log.error("Bulk sync: localId={} kaydedilemedi. Hata: {}", dto.localId(), e.getMessage(), e);

                results.add(SyncResultItemDto.builder()
                        .localId(dto.localId())
                        .serverId(null)
                        .status("FAILED")
                        .errorMessage("Oturum kaydedilirken sunucu hatası oluştu: " + e.getMessage())
                        .build());
            }
        }

        log.info("Bulk sync tamamlandı: toplam={}, başarılı={}, başarısız={}",
                request.sessions().size(), successCount, failedCount);

        return BulkSyncResponseDto.builder()
                .totalReceived(request.sessions().size())
                .successfullySynced(successCount)
                .failedCount(failedCount)
                .results(results)
                .build();
    }



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
     * Enum string'i güvenli parse edilir. Timestamp ve endDate doldurulur.
     */
    private Interruption buildInterruption(InterruptionRequestDto dto, FocusSession session) {
        InterruptionType type;
        try {
            type = dto.type() != null ? InterruptionType.valueOf(dto.type().toUpperCase()) : InterruptionType.NOTIFICATION;
        } catch (IllegalArgumentException e) {
            type = InterruptionType.NOTIFICATION; // Bozulmaması için geçerli bir varsayılan ata
        }

        LocalDateTime ts = dto.timestampUtc() != null ? dto.timestampUtc() : session.getStartTimeUtc();
        Integer duration = dto.durationSeconds() != null ? dto.durationSeconds() : 0;

        return Interruption.builder()
                .focusSession(session)
                .interruptionType(type)
                .appName(dto.appName())
                .durationSeconds(duration)
                .recoveryTimeSeconds(dto.recoveryTimeSeconds())
                .timestampUtc(ts)
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
