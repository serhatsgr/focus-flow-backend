package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.CognitiveLoadPointDto;
import com.serhatsgr.focusflow_backend.dto.DailyReportResponseDto;
import com.serhatsgr.focusflow_backend.dto.DailySessionItemDto;
import com.serhatsgr.focusflow_backend.entities.DailyRuleAnalysis;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.entities.User;
import com.serhatsgr.focusflow_backend.repository.DailyRuleAnalysisRepository;
import com.serhatsgr.focusflow_backend.repository.FocusSessionRepository;
import com.serhatsgr.focusflow_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyAnalyticsService {

    private final DailyRuleBasedAnalysisService ruleBasedAnalysisService;
    private final FocusSessionRepository focusSessionRepository;
    private final DailyRuleAnalysisRepository dailyRuleAnalysisRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"));

    @Transactional
    public DailyReportResponseDto getDailyReport(String email, LocalDate targetDate) {
        boolean isToday = targetDate.isEqual(LocalDate.now());
        String dateStr = targetDate.format(DATE_FORMATTER);

        // Importlara şunu eklemeyi unutma: import java.time.ZoneId; import java.time.ZonedDateTime;

        // 1. Kullanıcının yerel saat dilimini alıyoruz (Şimdilik Türkiye sabit, ileride User'dan gelebilir)
        ZoneId userZone = ZoneId.of("Europe/Istanbul");

        // 2. İstenen günün başlangıcını ve bitişini kullanıcının yerel saatinde buluyoruz
        ZonedDateTime localStart = targetDate.atStartOfDay(userZone);
        ZonedDateTime localEnd = targetDate.atTime(LocalTime.MAX).atZone(userZone);

        // 3. Veritabanındaki "startTimeUtc" ile kıyaslayabilmek için bunları UTC'ye çeviriyoruz
        LocalDateTime startOfDay = localStart.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endOfDay = localEnd.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        // 1. Dinamik Senaryo (Bugün)
        if (isToday) {
            List<FocusSession> dailySessions = focusSessionRepository.findAllByUser_EmailAndStartTimeUtcBetween(email, startOfDay, endOfDay);
            if (dailySessions == null || dailySessions.isEmpty()) {
                return buildEmptyReportForToday(dateStr);
            }
            return buildLiveReport(dailySessions, dateStr, true);
        } else {
            // 2. Lazy Evaluation Senaryosu (Geçmiş Günler)
            Optional<DailyRuleAnalysis> existingAnalysisOpt = dailyRuleAnalysisRepository.findByUser_EmailAndTargetDate(email, targetDate);

            if (existingAnalysisOpt.isPresent()) {
                // Cache HIT
                DailyRuleAnalysis existingAnalysis = existingAnalysisOpt.get();
                List<FocusSession> dailySessions = focusSessionRepository.findAllByUser_EmailAndStartTimeUtcBetween(email, startOfDay, endOfDay);
                return rebuildReportFromCache(existingAnalysis, dailySessions, dateStr);
            }

            // Cache MISS
            List<FocusSession> dailySessions = focusSessionRepository.findAllByUser_EmailAndStartTimeUtcBetween(email, startOfDay, endOfDay);
            if (dailySessions == null || dailySessions.isEmpty()) {
                return buildEmptyReportForPast(dateStr);
            }

            DailyReportResponseDto report = buildLiveReport(dailySessions, dateStr, false);
            saveAnalysisToDatabase(email, targetDate, dailySessions.size(), report);
            return report;
        }
    }

    private void saveAnalysisToDatabase(String email, LocalDate targetDate, int sessionCount, DailyReportResponseDto report) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        int totalDuration = report.getSessions().stream().mapToInt(DailySessionItemDto::getDurationMinutes).sum();

        DailyRuleAnalysis dailyAnalysis = DailyRuleAnalysis.builder()
                .user(user)
                .targetDate(targetDate)
                .analysisText(report.getAnalysisText())
                .sessionCount(sessionCount)
                .totalDurationMinutes(totalDuration)
                .build();

        dailyRuleAnalysisRepository.save(dailyAnalysis);
    }

    private DailyReportResponseDto buildEmptyReportForToday(String dateStr) {
        return DailyReportResponseDto.builder()
                .dateStr(dateStr)
                .isMidDay(true)
                .generalStatus("Veri Yok")
                .generalStatusDetail("Gün devam ediyor.")
                .graphData(new ArrayList<>())
                .sessions(new ArrayList<>())
                .analysisText("Bugün henüz oturum kaydetmedin.")
                .build();
    }

    private DailyReportResponseDto buildEmptyReportForPast(String dateStr) {
        return DailyReportResponseDto.builder()
                .dateStr(dateStr)
                .isMidDay(false)
                .generalStatus("Veri Yok")
                .generalStatusDetail("Analiz edilecek veri bulunamadı.")
                .graphData(new ArrayList<>())
                .sessions(new ArrayList<>())
                .analysisText("O gün oturum yapılmamış.")
                .build();
    }

    private DailyReportResponseDto rebuildReportFromCache(DailyRuleAnalysis existingAnalysis, List<FocusSession> sessions, String dateStr) {
        List<CognitiveLoadPointDto> graphData = new ArrayList<>();
        List<DailySessionItemDto> sessionList = new ArrayList<>();
        
        double totalScore = 0.0;
        int totalInterruptions = 0;
        
        for (FocusSession session : sessions) {
            double focusScore = session.getFinalFocusScore() != null ? session.getFinalFocusScore() : 0.0;
            int loadLevel = (int) Math.max(0, 100 - focusScore);
            String timeStr = session.getStartTimeUtc().format(TIME_FORMATTER);

            graphData.add(new CognitiveLoadPointDto(timeStr, loadLevel));

            int interruptionsCount = session.getInterruptions() != null ? session.getInterruptions().size() : 0;
            int duration = session.getActualDurationMinutes() != null ? session.getActualDurationMinutes() : session.getPlannedDurationMinutes();

            sessionList.add(DailySessionItemDto.builder()
                    .time(timeStr)
                    .durationMinutes(duration)
                    .interruptionsCount(interruptionsCount)
                    .build());
                    
            totalScore += focusScore;
            totalInterruptions += interruptionsCount;
        }
        
        double averageScore = sessions.isEmpty() ? 0 : totalScore / sessions.size();
        long seed = dateStr.hashCode() + (long) sessions.size() + totalInterruptions + existingAnalysis.getTotalDurationMinutes();
        
        DailyRuleBasedAnalysisService.GeneralStatusResult statusResult = ruleBasedAnalysisService
                .getGeneralStatus(averageScore, totalInterruptions, seed);

        return DailyReportResponseDto.builder()
                .dateStr(dateStr)
                .isMidDay(false)
                .generalStatus(sessions.isEmpty() ? "Veri Yok" : statusResult.title())
                .generalStatusDetail(sessions.isEmpty() ? "Analiz edilecek veri bulunamadı." : statusResult.subtitle())
                .graphData(graphData)
                .sessions(sessionList)
                .analysisText(existingAnalysis.getAnalysisText())
                .build();
    }

    private DailyReportResponseDto buildLiveReport(List<FocusSession> dailySessions, String dateStr, boolean isMidDay) {
        List<CognitiveLoadPointDto> graphData = new ArrayList<>();
        List<DailySessionItemDto> sessions = new ArrayList<>();

        double totalScore = 0.0;
        int totalInterruptions = 0;
        int totalDurationMinutes = 0;
        double morningTotalScore = 0.0;
        int morningCount = 0;
        double afternoonTotalScore = 0.0;
        int afternoonCount = 0;

        for (FocusSession session : dailySessions) {
            double focusScore = session.getFinalFocusScore() != null ? session.getFinalFocusScore() : 0.0;
            int loadLevel = (int) Math.max(0, 100 - focusScore);
            String timeStr = session.getStartTimeUtc().format(TIME_FORMATTER);

            graphData.add(new CognitiveLoadPointDto(timeStr, loadLevel));

            int interruptionsCount = session.getInterruptions() != null ? session.getInterruptions().size() : 0;
            int duration = session.getActualDurationMinutes() != null ? session.getActualDurationMinutes() : session.getPlannedDurationMinutes();

            sessions.add(DailySessionItemDto.builder()
                    .time(timeStr)
                    .durationMinutes(duration)
                    .interruptionsCount(interruptionsCount)
                    .build());

            totalScore += focusScore;
            totalInterruptions += interruptionsCount;
            totalDurationMinutes += duration;

            if (session.getStartTimeUtc().getHour() < 12) {
                morningTotalScore += focusScore;
                morningCount++;
            } else {
                afternoonTotalScore += focusScore;
                afternoonCount++;
            }
        }

        double averageScore = totalScore / dailySessions.size();
        double avgMorningScore = morningCount > 0 ? morningTotalScore / morningCount : 0.0;
        double avgAfternoonScore = afternoonCount > 0 ? afternoonTotalScore / afternoonCount : 0.0;

        long seed = dateStr.hashCode() + (long) dailySessions.size() + totalInterruptions + totalDurationMinutes;

        DailyRuleBasedAnalysisService.GeneralStatusResult statusResult = ruleBasedAnalysisService
                .getGeneralStatus(averageScore, totalInterruptions, seed);

        String analysisText = ruleBasedAnalysisService.generateFullAnalysisText(averageScore, avgMorningScore,
                avgAfternoonScore,
                morningCount, afternoonCount, totalDurationMinutes, totalInterruptions,
                dailySessions.size(), seed);

        String statusDetail = isMidDay 
                ? "Gün devam ediyor, gün sonu nihai analizin burada olacak." 
                : statusResult.subtitle();

        return DailyReportResponseDto.builder()
                .dateStr(dateStr)
                .isMidDay(isMidDay)
                .generalStatus(statusResult.title())
                .generalStatusDetail(statusDetail)
                .graphData(graphData)
                .sessions(sessions)
                .analysisText(analysisText)
                .build();
    }
}
