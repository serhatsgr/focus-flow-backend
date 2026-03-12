package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.dto.HeatmapPointDto;
import com.serhatsgr.focusflow_backend.dto.WeeklyHeatmapResponseDto;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import com.serhatsgr.focusflow_backend.repository.FocusSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyAnalyticsService {

    private final FocusSessionRepository focusSessionRepository;

    public WeeklyHeatmapResponseDto getWeeklyHeatmap(String email, LocalDate referenceDate, String timezoneId) {
        ZoneId tempZone;
        try {
            tempZone = ZoneId.of(timezoneId);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' provided for user '{}', falling back to UTC", timezoneId, email);
            tempZone = ZoneId.of("UTC");
        }
        final ZoneId zoneId = tempZone;

        // 1. Calculate boundaries in user's timezone
        ZonedDateTime userStartOfWeek = referenceDate.atStartOfDay(zoneId)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ZonedDateTime userEndOfWeek = referenceDate.atStartOfDay(zoneId)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        // 2. Convert boundaries to UTC for DB query
        LocalDateTime startUtc = userStartOfWeek.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        LocalDateTime endUtc = userEndOfWeek.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();

        // 3. Fetch data from DB
        List<FocusSession> sessions = focusSessionRepository.findAllByUser_EmailAndStartTimeUtcBetween(
                email, startUtc, endUtc);

        // 4. Group by DayOfWeek and Hour in the user's timezone
        Map<DayOfWeek, Map<Integer, Integer>> groupedDuration = sessions.stream()
                .filter(session -> session.getActualDurationMinutes() != null && session.getActualDurationMinutes() > 0)
                .map(session -> {
                    ZonedDateTime userLocalTime = session.getStartTimeUtc()
                            .atZone(ZoneId.of("UTC"))
                            .withZoneSameInstant(zoneId);
                    return Map.entry(userLocalTime, session.getActualDurationMinutes());
                })
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().getDayOfWeek(),
                        Collectors.groupingBy(
                                entry -> entry.getKey().getHour(),
                                Collectors.summingInt(Map.Entry::getValue))));

        // 5. Build Heatmap Data list
        List<HeatmapPointDto> heatmapData = groupedDuration.entrySet().stream()
                .flatMap(dayEntry -> dayEntry.getValue().entrySet().stream()
                        .map(hourEntry -> HeatmapPointDto.builder()
                                .dayOfWeek(dayEntry.getKey().name())
                                .hour(hourEntry.getKey())
                                .intensity(calculateIntensity(hourEntry.getValue()))
                                .build()))
                .filter(dto -> dto.getIntensity() > 0) // Only include hours with sessions
                .collect(Collectors.toList());

        // 6. Return response
        return WeeklyHeatmapResponseDto.builder()
                .startDate(userStartOfWeek.toLocalDate())
                .endDate(userEndOfWeek.toLocalDate())
                .heatmapData(heatmapData)
                .build();
    }

    private int calculateIntensity(int totalMinutes) {
        if (totalMinutes == 0)
            return 0;
        if (totalMinutes <= 15)
            return 1;
        if (totalMinutes <= 30)
            return 2;
        if (totalMinutes <= 45)
            return 3;
        return 4;
    }
}
