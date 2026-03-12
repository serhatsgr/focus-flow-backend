package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.CognitiveLoadPointDto;
import com.serhatsgr.focusflow_backend.dto.DailyReportResponseDto;
import com.serhatsgr.focusflow_backend.dto.DailySessionItemDto;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DailyAnalyticsService {

        private final DailyRuleBasedAnalysisService ruleBasedAnalysisService;
        private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy",
                        Locale.forLanguageTag("tr-TR"));

        public DailyReportResponseDto generateDailyReport(List<FocusSession> dailySessions) {
                if (dailySessions == null || dailySessions.isEmpty()) {
                        return DailyReportResponseDto.builder()
                                        .dateStr("")
                                        .generalStatus("Veri Yok")
                                        .generalStatusDetail("Analiz edilecek oturum bulunamadı.")
                                        .graphData(new ArrayList<>())
                                        .sessions(new ArrayList<>())
                                        .analysisText("Yeterli veri yok.")
                                        .build();
                }

                List<CognitiveLoadPointDto> graphData = new ArrayList<>();
                List<DailySessionItemDto> sessions = new ArrayList<>();

                double totalScore = 0.0;
                int totalInterruptions = 0;
                int totalDurationMinutes = 0;

                double morningTotalScore = 0.0;
                int morningCount = 0;

                double afternoonTotalScore = 0.0;
                int afternoonCount = 0;

                String dateStr = dailySessions.get(0).getStartTimeUtc().format(DATE_FORMATTER);

                for (FocusSession session : dailySessions) {
                        double focusScore = session.getFinalFocusScore() != null ? session.getFinalFocusScore() : 0.0;
                        int loadLevel = (int) Math.max(0, 100 - focusScore);
                        String timeStr = session.getStartTimeUtc().format(TIME_FORMATTER);

                        graphData.add(new CognitiveLoadPointDto(timeStr, loadLevel));

                        int interruptionsCount = session.getInterruptions() != null ? session.getInterruptions().size()
                                        : 0;
                        int duration = session.getActualDurationMinutes() != null ? session.getActualDurationMinutes()
                                        : session.getPlannedDurationMinutes();

                        sessions.add(DailySessionItemDto.builder()
                                        .time(timeStr)
                                        .durationMinutes(duration)
                                        .interruptionsCount(interruptionsCount)
                                        .build());

                        totalScore += focusScore;
                        totalInterruptions += interruptionsCount;
                        totalDurationMinutes += duration;

                        // Before 12:00 is morning
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

                long seed = dateStr.hashCode() + (long) dailySessions.size() + totalInterruptions
                                + totalDurationMinutes;

                DailyRuleBasedAnalysisService.GeneralStatusResult statusResult = ruleBasedAnalysisService
                                .getGeneralStatus(averageScore, totalInterruptions, seed);

                String analysisText = ruleBasedAnalysisService.generateFullAnalysisText(averageScore, avgMorningScore,
                                avgAfternoonScore,
                                morningCount, afternoonCount, totalDurationMinutes, totalInterruptions,
                                dailySessions.size(), seed);

                return DailyReportResponseDto.builder()
                                .dateStr(dateStr)
                                .generalStatus(statusResult.title())
                                .generalStatusDetail(statusResult.subtitle())
                                .graphData(graphData)
                                .sessions(sessions)
                                .analysisText(analysisText)
                                .build();
        }
}
