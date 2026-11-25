package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.WeeklyAiResponseDto;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.entities.WeeklyAiReport;
import com.serhatsgr.focusflow_backend.repository.FocusSessionRepository;
import com.serhatsgr.focusflow_backend.repository.WeeklyAiReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Haftalık AI Rapor Üretim Scheduler'ı.
 *
 * Her Pazar gecesi 23:00 (Europe/Istanbul) tetiklenir.
 * Kullanıcıları 50'şerlik sayfalar hâlinde DB'den çeker,
 * her kullanıcı için LLM raporunu üretir ve DB'ye kaydeder.
 *
 * Koruma mekanizmaları:
 * - Pagination: RAM'i korur — tüm kullanıcılar asla tek seferde belleğe yüklenmez
 * - Rate Limit Delay: Her rapor sonrası 3 saniye bekleme — LLM API 429 koruması
 * - Per-User Error Isolation: Bir kullanıcının hatası döngüyü kesmez
 * - Deduplication: Zaten raporu olan kullanıcı atlanır
 */
@Component
public class WeeklyAiReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyAiReportScheduler.class);
    private static final int BATCH_SIZE = 50;
    private static final int RATE_LIMIT_DELAY_MS = 3000;

    private final FocusSessionRepository focusSessionRepository;
    private final WeeklyAiReportRepository weeklyAiReportRepository;
    private final WeeklyAiCoachService weeklyAiCoachService;

    public WeeklyAiReportScheduler(FocusSessionRepository focusSessionRepository,
            WeeklyAiReportRepository weeklyAiReportRepository,
            WeeklyAiCoachService weeklyAiCoachService) {
        this.focusSessionRepository = focusSessionRepository;
        this.weeklyAiReportRepository = weeklyAiReportRepository;
        this.weeklyAiCoachService = weeklyAiCoachService;
    }

    /**
     * Her Pazar 23:00 (Europe/Istanbul) tetiklenen batch job.
     *
     * İş akışı:
     * 1. Bu haftanın Pazartesi–Pazar aralığını hesapla
     * 2. Bu aralıkta en az 1 oturumu olan kullanıcıları sayfala
     * 3. Her kullanıcı için: rapor üret → DB'ye kaydet → 3s bekle
     * 4. Sonuç istatistiklerini logla
     */
    //test @Scheduled(cron = "0 * * * * *", zone = "Europe/Istanbul")
    @Scheduled(cron = "0 0 23 * * SUN", zone = "Europe/Istanbul")
    public void generateWeeklyReports() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        LocalDateTime rangeStart = weekStart.atStartOfDay();
        LocalDateTime rangeEnd = weekEnd.atTime(23, 59, 59);

        log.info("══════════════════════════════════════════════════════════════");
        log.info("NEUROCOACH BATCH — Haftalık rapor üretimi başlıyor");
        log.info("Hafta aralığı: {} → {}", weekStart, weekEnd);
        log.info("══════════════════════════════════════════════════════════════");

        int page = 0;
        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalFailed = 0;
        int totalSkipped = 0;

        while (true) {
            Page<User> userPage = focusSessionRepository
                    .findDistinctUsersWithSessionsBetween(rangeStart, rangeEnd, PageRequest.of(page, BATCH_SIZE));

            if (userPage.isEmpty()) {
                break;
            }

            log.info("BATCH — Sayfa {} işleniyor ({} kullanıcı)", page + 1, userPage.getNumberOfElements());

            for (User user : userPage.getContent()) {
                totalProcessed++;

                try {
                    // ── Mükerrer kontrol ──
                    if (weeklyAiReportRepository.existsByUserIdAndWeekStartDate(user.getId(), weekStart)) {
                        log.debug("BATCH — Kullanıcı {} için rapor zaten mevcut, atlanıyor.", user.getId());
                        totalSkipped++;
                        continue;
                    }

                    // ── Rapor üret ──
                    WeeklyAiResponseDto dto = weeklyAiCoachService.generateWeeklyAnalysis(
                            user.getEmail(), weekEnd);

                    // ── DB'ye kaydet ──
                    WeeklyAiReport report = WeeklyAiReport.builder()
                            .user(user)
                            .weekStartDate(weekStart)
                            .weekEndDate(weekEnd)
                            .theme(dto.weeklyTheme())
                            .summary(dto.weeklySummary())
                            .strengths(dto.strengths() != null ? dto.strengths() : java.util.List.of())
                            .weaknesses(dto.weaknesses() != null ? dto.weaknesses() : java.util.List.of())
                            .actionableAdvice(dto.actionableAdvice())
                            .build();

                    weeklyAiReportRepository.save(report);
                    totalSuccess++;

                    log.info("BATCH — ✓ Kullanıcı {} raporu kaydedildi [tema={}]",
                            user.getId(), dto.weeklyTheme());

                    // ── Rate Limit Koruması ──
                    Thread.sleep(RATE_LIMIT_DELAY_MS);

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("BATCH — Thread interrupt sinyali alındı, batch durduruluyor.");
                    break;
                } catch (Exception e) {
                    totalFailed++;
                    log.error("BATCH — ✗ Kullanıcı {} raporu üretilemedi: {}",
                            user.getId(), e.getMessage(), e);
                    // Döngü devam eder — diğer kullanıcılar etkilenmez
                }
            }

            // ── Sonraki sayfa var mı? ──
            if (!userPage.hasNext()) {
                break;
            }
            page++;
        }

        log.info("══════════════════════════════════════════════════════════════");
        log.info("NEUROCOACH BATCH — Tamamlandı");
        log.info("  İşlenen  : {}", totalProcessed);
        log.info("  Başarılı : {}", totalSuccess);
        log.info("  Atlanan  : {} (zaten mevcut)", totalSkipped);
        log.info("  Başarısız: {}", totalFailed);
        log.info("══════════════════════════════════════════════════════════════");
    }
}
