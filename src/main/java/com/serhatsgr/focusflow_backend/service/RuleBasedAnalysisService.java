package com.serhatsgr.focusflow_backend.service;

import com.serhatsgr.focusflow_backend.entities.FocusMode;
import com.serhatsgr.focusflow_backend.entities.FocusSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * Kural Tabanlı Oturum Analiz Motoru.
 *
 * LLM API çağrısı yapmadan, oturum verilerine göre koşullu seçim yaparak
 * gerçekçi ve rastgele Markdown formatında bilimsel analiz metni üretir.
 *
 * Mimari:
 * - Katman 1: Yönetici Özeti (Mod × Skor kombinasyonu)
 * - Katman 2: Süre İçgörüleri (skor farkındalıklı — aynı süre farklı skorda
 * farklı metin)
 * - Katman 3: Kesinti ve İyileşme İçgörüleri
 * - Katman 4: Biyofeedback (yalnızca hasWearableData == true ise)
 *
 * Her katmandaki havuzdan rastgele bir cümle seçilir — her çağrıda farklı
 * metin üretilir, LLM benzeri bir dinamizm sağlanır.
 */
@Service
public class RuleBasedAnalysisService {

        private final Random random = new Random();

        // ─────────────────────────────────────────────────────────────────────────
        // KATMAN 1: Yönetici Özeti — Mod × Skor Kombinasyonları
        // ─────────────────────────────────────────────────────────────────────────

        /** STRICT mod, yüksek skor (>= 80) */
        private static final List<String> STRICT_HIGH = List.of(
                        "STRICT modunun sıfır toleranslı yapısına mükemmel uyum sağladın. " +
                                        "Nörolojik ağların tek bir hedefe kilitlendi ve tam bir kognitif izolasyon sağladın.",
                        "Katı kurallara rağmen bilişsel dayanıklılığın zirvedeydi. " +
                                        "Hiper-odak (hyper-focus) haline geçişin son derece pürüzsüzdü.");

        /** STRICT mod, düşük skor (< 80) */
        private static final List<String> STRICT_LOW = List.of(
                        "STRICT modun acımasız cezalandırma sistemi, yaşadığın mikro kopmaları affetmedi. " +
                                        "Bu mod, dış dünyayla arana çok daha kalın bir duvar örmeni talep eder.",
                        "Derin çalışma niyetin güçlü olsa da, katı kurallar altında yaşanan bağlam kopuşları " +
                                        "prefrontal korteksini yordu.");

        /** DEEP (veya null) mod, yüksek skor (>= 75) */
        private static final List<String> DEEP_HIGH = List.of(
                        "İnsan doğasına en uygun olan DEEP modda harika bir ritim yakaladın. " +
                                        "Zihinsel deponu tüketmeden sürdürülebilir bir akış (flow) sağladın.",
                        "Bilişsel kapasiteni çok verimli kullandın. Dış uyaranları filtreleme başarın takdire şayan.");

        /** DEEP (veya null) mod, düşük skor (< 75) */
        private static final List<String> DEEP_LOW = List.of(
                        "Odaklanma kasın direnmeye çalışsa da zihinsel dalgalanmalar yaşadın. " +
                                        "Karar yorgunluğu (decision fatigue) odağını gizlice aşağı çekti.");

        /** FLEXIBLE mod, yüksek skor (>= 80) */
        private static final List<String> FLEXIBLE_HIGH = List.of(
                        "FLEXIBLE modun tanıdığı esnekliğe rağmen odağını manipüle etmelerine izin vermedin. " +
                                        "Bilinçli ve kontrollü bir seanstı.");

        /** FLEXIBLE mod, düşük skor (< 80) */
        private static final List<String> FLEXIBLE_LOW = List.of(
                        "Esnek tolerans limitlerine rağmen skorunun düşmesi, çok fazla bağlam değişimi " +
                                        "yaşadığını gösteriyor. Zihnin 'dikkat artığı' (attention residue) ile dolmuş durumda.");

        // ─────────────────────────────────────────────────────────────────────────
        // KATMAN 2: Süre İçgörüleri (skor farkındalıklı)
        // ─────────────────────────────────────────────────────────────────────────

        /** Erken çıkış: actual < planned */
        private static final List<String> EARLY_EXIT = List.of(
                        "Planlanan süreden erken ayrıldın. Dopaminerjik ödül döngün tamamlanmadan " +
                                        "görevi bırakman mental tükenmişlik belirtisi olabilir.");

        /** Aşırı çalışma: actual > 60 */
        private static final List<String> OVERWORK = List.of(
                        "Hedefini aştın ancak 60 dakikanın üzerindeki kesintisiz bloklarda 'azalan verim kanunu' başlar. "
                                        +
                                        "Bilişsel aşırı yüklenmeden (cognitive overload) kaçınmalısın.");

        /** Altın pencere (35-50 dk) + yüksek skor (>= 75) */
        private static final List<String> GOLDEN_RATIO_HIGH = List.of(
                        "İnsan beyninin ideal ultradiyen ritmi olan ~40 dakikalık altın pencereyi kusursuz kullandın.");

        /** Altın pencere (35-50 dk) + düşük skor (< 75) */
        private static final List<String> GOLDEN_RATIO_LOW = List.of(
                        "Masa başında ~40 dakika kalarak fiziksel hedefini tutturdun, ancak zihnen masada değildin. " +
                                        "Süreyi doldurmak her zaman derin çalışmak anlamına gelmez.");

        /** Standart süre + yüksek skor (>= 75) */
        private static final List<String> STANDARD_DURATION_HIGH = List.of(
                        "Planladığın süreye sadık kalarak hedefleme disiplinini korudun.");

        /** Standart süre + düşük skor (< 75) */
        private static final List<String> STANDARD_DURATION_LOW = List.of(
                        "Fiziksel olarak planladığın süreyi tamamlasan da, yaşadığın bilişsel kopuşlar nedeniyle " +
                                        "bu sürenin hakkını tam olarak veremedin.");

        // ─────────────────────────────────────────────────────────────────────────
        // KATMAN 3: Kesinti ve İyileşme İçgörüleri
        // ─────────────────────────────────────────────────────────────────────────

        /** Hiç kesinti yok */
        private static final List<String> NO_INTERRUPTION = List.of(
                        "Sıfır bağlam değişimi! Nöronal yollarını tek bir göreve adadın ve " +
                                        "görevler arası geçiş maliyeti (task-switching cost) ödemedin.");

        /** Yüksek uygulama geçişi: appSwitchCount >= 2 */
        private static final List<String> HIGH_SWITCH = List.of(
                        "Uygulamalar arası geçişler beyninde kalıntılar bıraktı. " +
                                        "Her uygulama değişimi, odağına geri dönmek için ekstra glikoz harcamana neden oldu.");

        /** Yavaş iyileşme: avgRecoveryTimeSeconds > 60 */
        private static final List<String> SLOW_RECOVERY = List.of(
                        "Bir kesintiden sonra toparlanma süren çok uzundu. " +
                                        "Bu durum sende sadece zaman değil, ciddi bir 'momentum' kaybı yarattı.");

        /** Hafif kesintiler (diğer durumlar) */
        private static final List<String> MINOR_INTERRUPTIONS = List.of(
                        "Ufak tefek dikkat dağıtıcılar olsa da nörolojik esnekliğin sayesinde " +
                                        "hızla toparlanmayı başardın.");

        // ─────────────────────────────────────────────────────────────────────────
        // KATMAN 4: Biyofeedback (hasWearableData == true iken)
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Nabız dalgalı (hrVariance > 10.0) veya ortalama yüksek (avgHeartRate > 90.0)
         */
        private static final List<String> PHYSIO_STRESSED = List.of(
                        "Nabız dalgalanmaların ve kalp ritmin sempatik sinir sisteminin (savaş-veya-kaç) tetiklendiğini gösteriyor. "
                                        +
                                        "Bilişsel bir zorlanma veya mikro-stresler yaşamışsın.");

        /** Fazla rahat / adrenalin eksik (hrVariance <= 10.0 && avgHeartRate < 75.0) */
        private static final List<String> PHYSIO_TOO_RELAXED = List.of(
                        "Fiziksel olarak 'çok fazla rahat' bir evredesin (parasempatik dominans). " +
                                        "Zihni keskin tutacak o ufak, sağlıklı adrenalin (eustress) eksik olduğu için sistem maksimum potansiyelini kısıtladı.");

        /** Optimal akış bölgesi (hrVariance <= 10.0 && 75.0 <= avgHeartRate <= 90.0) */
        private static final List<String> PHYSIO_OPTIMAL = List.of(
                        "Kalp ritmin ve stabiliten kusursuz. Biyometrik verilerin, ne fazla stresli ne de fazla gevşek olduğun "
                                        +
                                        "o mükemmel 'Akış' (Flow) zonunda kaldığını doğruluyor.");

        // ─────────────────────────────────────────────────────────────────────────
        // Ana Metot
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Oturum verilerinden koşullu seçim yaparak Markdown formatında analiz metni
         * üretir.
         *
         * @param session Tamamlanmış FocusSession (null skorlar 0 kabul edilir)
         * @return Markdown formatında oturum analizi
         */
        public String generateSessionAnalysis(FocusSession session) {
                int score = (int) Math.round(orZero(session.getFinalFocusScore()));
                int actual = orZeroInt(session.getActualDurationMinutes());
                int planned = orZeroInt(session.getPlannedDurationMinutes());
                int interruptions = session.getInterruptions() != null ? session.getInterruptions().size() : 0;
                int appSwitches = orZeroInt(session.getAppSwitchCount());
                double avgRecovery = orZero(session.getAvgRecoveryTimeSeconds());
                FocusMode mode = session.getMode();
                boolean hasWearable = Boolean.TRUE.equals(session.getHasWearableData());

                StringBuilder sb = new StringBuilder();

                // ── Katman 1: Yönetici Özeti ─────────────────────────────────────────
                sb.append("**\uD83E\uDDE0 Yönetici Özeti**\n");
                sb.append(selectExecutiveSummary(mode, score));
                sb.append("\n\n");

                // ── Katman 2 + 3: Kognitif İçgörüler ─────────────────────────────────
                sb.append("**\uD83D\uDCCA Kognitif İçgörüler**\n");
                sb.append("* ").append(selectDurationInsight(actual, planned, score)).append("\n");
                sb.append("* ").append(selectInterruptionInsight(interruptions, appSwitches, avgRecovery));
                sb.append("\n\n");

                // ── Katman 4: Biyofeedback (yalnızca wearable varsa) ─────────────────
                if (hasWearable && session.getHrVariance() != null) {
                        sb.append("**\uD83E\uDEC0 Biyofeedback Analizi**\n");
                        sb.append("* ").append(selectPhysioInsight(session.getHrVariance(), session.getAvgHeartRate()));
                }

                return sb.toString().trim();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Katman Seçim Metodları
        // ─────────────────────────────────────────────────────────────────────────

        /** Mod ve skora göre katman 1 cümlesini seçer. */
        private String selectExecutiveSummary(FocusMode mode, int score) {
                if (mode == FocusMode.STRICT) {
                        return getRandom(score >= 80 ? STRICT_HIGH : STRICT_LOW);
                }
                if (mode == FocusMode.FLEXIBLE) {
                        return getRandom(score >= 80 ? FLEXIBLE_HIGH : FLEXIBLE_LOW);
                }
                // DEEP veya null
                return getRandom(score >= 75 ? DEEP_HIGH : DEEP_LOW);
        }

        /**
         * Süre ve skora göre katman 2 cümlesini seçer.
         * Skor farkındalığı: aynı süre aynı metin yerine skora göre farklı
         * değerlendirme döner.
         */
        private String selectDurationInsight(int actual, int planned, int score) {
                if (actual < planned)
                        return getRandom(EARLY_EXIT);
                if (actual > 60)
                        return getRandom(OVERWORK);
                if (actual >= 35 && actual <= 50) {
                        return getRandom(score >= 75 ? GOLDEN_RATIO_HIGH : GOLDEN_RATIO_LOW);
                }
                return getRandom(score >= 75 ? STANDARD_DURATION_HIGH : STANDARD_DURATION_LOW);
        }

        /** Kesinti durumuna göre katman 3 cümlesini seçer. */
        private String selectInterruptionInsight(int interruptionsCount, int appSwitches, double avgRecovery) {
                if (interruptionsCount == 0)
                        return getRandom(NO_INTERRUPTION);
                if (appSwitches >= 2)
                        return getRandom(HIGH_SWITCH);
                if (avgRecovery > 60.0)
                        return getRandom(SLOW_RECOVERY);
                return getRandom(MINOR_INTERRUPTIONS);
        }

        /** HR varyansına ve ortalama Kalp Atış Hızına göre katman 4 cümlesini seçer. */
        private String selectPhysioInsight(double hrVariance, Double avgHeartRate) {
                double hr = avgHeartRate != null ? avgHeartRate : 70.0;
                if (hrVariance > 10.0 || hr > 90.0) {
                        return getRandom(PHYSIO_STRESSED);
                } else if (hr < 75.0) {
                        return getRandom(PHYSIO_TOO_RELAXED);
                } else {
                        return getRandom(PHYSIO_OPTIMAL);
                }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Yardımcılar
        // ─────────────────────────────────────────────────────────────────────────

        /** Verilen havuzdan rastgele bir eleman seçer. */
        private String getRandom(List<String> pool) {
                return pool.get(random.nextInt(pool.size()));
        }

        private Double orZero(Double value) {
                return value != null ? value : 0.0;
        }

        private int orZeroInt(Integer value) {
                return value != null ? value : 0;
        }
}
