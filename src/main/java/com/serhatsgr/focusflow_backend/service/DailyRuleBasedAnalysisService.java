package com.serhatsgr.focusflow_backend.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class DailyRuleBasedAnalysisService {

    public record GeneralStatusResult(String title, String subtitle) {
    }

    private String getRandom(List<String> pool, Random random) {
        if (pool == null || pool.isEmpty())
            return "";
        return pool.get(random.nextInt(pool.size()));
    }

    public GeneralStatusResult getGeneralStatus(double averageScore, int totalInterruptions, long seed) {
        Random random = new Random(seed);
        if (averageScore >= 85 && totalInterruptions == 0) {
            List<String> hiperOdakPool = List.of(
                    "Bilişsel kapasitenin sınırlarını zorladığın kusursuz bir gün.",
                    "Derin çalışma evresine kesintisiz giriş yaptığın mükemmel bir nöro-verimlilik tablosu.",
                    "Prefrontal korteksinin (beyninin odak ve karar verme merkezinin) maksimum verimle çalıştığı, üst düzey odaklanma sergilediğin bir periyot.");
            return new GeneralStatusResult("Hiper-Odak", getRandom(hiperOdakPool, random));
        } else if (totalInterruptions > 5 || averageScore < 60) {
            List<String> daginikPool = List.of(
                    "Zihinsel yükünün ve bağlam kopuşlarının yüksek olduğu bir gün.",
                    "Sürekli bölünen dikkatin nedeniyle bilişsel tükenmişlik belirtileri gösterdiğin bir süreç.",
                    "Nörolojik esnekliğini (dikkat dağıtıcılardan sonra hızla toparlanma becerini) zorlayan, sık sık odak kaybı yaşadığın düşük verimli bir gün.");
            return new GeneralStatusResult("Dağınık", getRandom(daginikPool, random));
        } else if (averageScore >= 75) {
            List<String> stabilPool = List.of(
                    "Gün boyu odak stabiliteni korumayı başardın.",
                    "Bilişsel dalgalanmaları iyi yönettiğin ve sürdürülebilir bir tempo tutturduğun bir oturum zinciri.",
                    "Dış uyaranlara karşı dirençli kaldığın ve zihinsel enerjini koruduğun dengeli bir performans.");
            return new GeneralStatusResult("Stabil", getRandom(stabilPool, random));
        } else {
            List<String> dengeliPool = List.of(
                    "Gün içinde iniş çıkışlar yaşasan da genel olarak yönetilebilir bir gündü.",
                    "Nöronal ağlarını (beynindeki bilgi işlem yollarını) ne çok zorladığın ne de tamamen serbest bıraktığın ortalama bir efor seviyesi.",
                    "Odaklanma kalitende dalgalanmalar olsa da günü makul bir bilişsel yükle tamamladın.");
            return new GeneralStatusResult("Dengeli", getRandom(dengeliPool, random));
        }
    }

    public String generateFullAnalysisText(double morningScore, double afternoonScore, int morningCount,
            int afternoonCount, int totalDurationMinutes, int totalInterruptions, int sessionCount, long seed) {
        Random seededRandom = new Random(seed);

        String temporalText = getTemporalInsight(morningScore, afternoonScore, morningCount, afternoonCount,
                seededRandom);
        String volumeText = getVolumeInsight(totalDurationMinutes, seededRandom);
        String coachingText = getCoachingInsight(totalInterruptions, sessionCount, seededRandom);

        List<String> volumeTransitions = List.of(
                "Bununla birlikte günlük hacmini de değerlendirelim.",
                "Fiziksel kapasitene dönecek olursak durum şu şekilde:",
                "Günlük eforunu incelediğimizde ilginç bir tablo var.");

        List<String> coachingTransitions = List.of(
                "Davranışsal strateji olarak şuna dikkat etmeliyiz:",
                "Gelecek seanslar için küçük bir ipucu vermek gerekirse:",
                "Son bir nörolojik not ekleyelim:");

        String transition1 = getRandom(volumeTransitions, seededRandom);
        String transition2 = coachingText.isEmpty() ? "" : getRandom(coachingTransitions, seededRandom);

        StringBuilder fullText = new StringBuilder(temporalText);
        if (!volumeText.isEmpty()) {
            fullText.append(" ").append(transition1).append(" ").append(volumeText);
        }
        if (!coachingText.isEmpty()) {
            fullText.append(" ").append(transition2).append(" ").append(coachingText);
        }

        return fullText.toString().trim();
    }

    private String getTemporalInsight(double morningScore, double afternoonScore, int morningCount, int afternoonCount,
            Random random) {
        List<String> morningBetterPool = List.of(
                "Öğleden sonra zihinsel yük artışı gözlemlendi. Odak stabilitesi sabah saatlerinde daha yüksekti.",
                "Sirkadiyen ritminin (vücudunun doğal biyolojik saatinin) sabah saatlerinde zirve yaptığı görülüyor, günün ilerleyen kısımlarında nöronal yorgunluk başlamış.",
                "Erken saatlerde prefrontal kapasiten (beyninin odak ve karar verme merkezi) aktif çalışırken, öğleden sonra doğal bir düşüş yaşanmış.",
                "Gün doğumuyla yakaladığın o yüksek kognitif frekans, akşama doğru yerini yavaşlamaya bırakmış.",
                "Sabah bloklarındaki zihinsel berraklığı öğleden sonraya taşıyamamışsın, bu da sirkadiyen (günlük döngüsel) bir düşüşe işaret ediyor.");
        List<String> afternoonBetterPool = List.of(
                "Güne dağınık başlasan da, öğleden sonra sirkadiyen ritmini (doğal biyolojik saatini) yakalayıp derin odaklanmayı başardın.",
                "Günün ikinci yarısında prefrontal korteksin (beyninin odak merkezi) kontrolü ele aldı ve zihinsel akış evresine girdin.",
                "Sabahki sisli başlangıcın ardından öğleden sonra nöropsikolojik verimliliğinde belirgin bir sıçrama kaydettin.",
                "Sirkadiyen biyolojik saatin açıkça öğleden sonra uyandığını gösteren çarpıcı bir performans eğrisi çizdin.",
                "Öğleden sonraki nöronal ateşlemelerin çok daha kararlıydı; günün ilk saatlerini adeta zihinsel ısınma turu olarak kullanmışsın.");
        List<String> consistentPool = List.of(
                "Gün boyu bilişsel performansında dalgalanma yaşanmadı. Nörolojik esnekliğin (hızla toparlanma becerin) günün her saatinde devredeydi.",
                "Sabah ve öğleden sonra eforun stabil kaldı. Bilişsel rezervlerini (günlük zihinsel enerjini) tüm güne çok başarılı bir şekilde yaydın.",
                "Kognitif yük dağılımın gün içine eşit yayılmış, sürdürülebilir bir nöronal tempo (zihinsel kararlılık) oluşturmuşsun.",
                "Prefrontal yürütücü işlevlerin gün boyunca sabit bir frekansta çalışarak sana sarsılmaz bir kararlılık sağladı.",
                "Düz bir nöro-verimlilik platosunda seyrettin; ne ani bir bilişsel tükenmişlik hissettin ne de yersiz bir gerginlik yaşadın.");
        List<String> onlyMorningPool = List.of(
                "Bugün derin çalışma bloklarını erken bitirdin. Günün geri kalanında zihnine toparlanma (recovery) izni verebilirsin.",
                "Odak eforunu yalnızca sabah saatlerine topladın. Öğleden sonrayı bilişsel rezervlerini (zihinsel enerjini) yenilemek için kullanıyorsun.",
                "Günün erken bölümünde yüksek verim gösterdin, zihinsel deponu erkenden tüketmek sirkadiyen ritminle uyumlu bir strateji olabilir.",
                "Sabahın o taze ve bölünmemiş nöral ortamını çok iyi kullandın, günün geri kalanını prefrontal korteksinin (odak merkezinin) dinlenmesine ayırabilirsin.",
                "Kognitif rezervlerini günün başlangıcında tamamen yönettin; bu da hızlı ve çevik bir başarı getirdi.");
        List<String> onlyAfternoonPool = List.of(
                "Güne geç başlasan da öğleden sonraki bloklarda hiper-odak evresine girmeyi başardın.",
                "Bilişsel uyanışın günün ikinci yarısında gerçekleşti, akşamüstüne doğru nöronal ağların (bilgi işlem yolların) tam kapasiteye ulaştı.",
                "Sabah saatlerini es geçip tüm zihinsel yükü günün ikinci yarısına bıraktın ve sirkadiyen ritminle uyumlu olarak başarıyla yönettin.",
                "Biyolojik saatinin sana ancak öğlen sonrası yeşil ışık yaktığı bu günde prefrontal kapasiteni (odak merkezini) yüksek perdeden açtın.",
                "Gecikmeli başlayan bir efor olsa da, ikinci yarıda yakaladığın bilişsel akış (flow) sabahın eksikliğini tamamen telafi etti.");

        if (morningCount > 0 && afternoonCount == 0) {
            return getRandom(onlyMorningPool, random);
        } else if (morningCount == 0 && afternoonCount > 0) {
            return getRandom(onlyAfternoonPool, random);
        } else {
            // İki tarafta da oturum varsa mevcut kıyaslama mantığını çalıştır
            if (Math.abs(morningScore - afternoonScore) <= 5.0) {
                return getRandom(consistentPool, random);
            } else if (morningScore > afternoonScore) {
                return getRandom(morningBetterPool, random);
            } else {
                return getRandom(afternoonBetterPool, random);
            }
        }
    }

    private String getVolumeInsight(int totalDurationMinutes, Random random) {
        if (totalDurationMinutes > 240) {
            List<String> heavyLoadPool = List.of(
                    "Bugün nöronal ağlarını (beynindeki bilgi işlem yollarını) ciddi şekilde yordun. Yoğun mesainin ardından uyku kalitene ekstra dikkat etmelisin.",
                    "Aşırı yüksek bilişsel hacme ulaştın. Prefrontal kapasitenin toparlanması için yarınki temponu hafifletmeyi düşünebilirsin.",
                    "Sınırları zorlayan bir odaklanma maratonu geçirdin. Beyninin enerji depoları tükendiği için derin nörolojik dinlenmeye ihtiyacın var.",
                    "Gerçek bir bilişsel dayanıklılık testi yaptın; ancak bu seviyede bir nöral yük uzun vadede kortizol (stres hormonu) birikimi yaratabilir.",
                    "Bilişsel rezervlerini (zihinsel enerjini) son damlasına kadar kullandın, şimdi beynin kendini onarması (nöroplastisite) için derin uyku şart.");
            return getRandom(heavyLoadPool, random);
        } else if (totalDurationMinutes < 60) {
            List<String> lightLoadPool = List.of(
                    "Bilişsel rezervlerini (günlük zihinsel enerjini) tüketmediğin, hafif bir gündü. Yarın daha büyük odak blokları planlayabilirsin.",
                    "Prefrontal kapasiteni (odak merkezini) oldukça idareli kullandın. Uzun hiper-odak seansları için hala fazlasıyla yakıtın var.",
                    "Minimal nöral stresle geçen düşük hacimli bir gündü. Yarın daha zorlu görevler için zihnin tamamen hazır.",
                    "Bilişsel eşiğini zorlamadın ki bu da yaklaşan zorlu görevler için güç toplamaktır. (Bir nevi aktif dinlenme günü.)",
                    "Nöronal ağlarını rölantide çalıştırdın. Zihnindeki potansiyel enerji zorlu problemler için yakılmayı bekliyor.");
            return getRandom(lightLoadPool, random);
        } else {
            List<String> optimalLoadPool = List.of(
                    "Zihinsel kapasiteni (kognitif eşiğini) aşırı zorlamadan, sürdürülebilir bir günlük efor sarf ettin.",
                    "Nöro-verimlilik açısından ideal hacimde bir gündü. Bilişsel sınırlarını koruyarak dengeli çalıştın.",
                    "Oturum sürelerin tam kararında; nöronal ağlarını (bilgi yollarını) tükenmişliğe sürüklemeden verimli sonuçlar elde ettin.",
                    "Altın oranda bir performans gösterdin; ne prefrontal yorgunluk hissedecek kadar aşırı, ne de potansiyelini harcayacak kadar az.",
                    "Tam olması gerektiği kadar bir nöral efor sergiledin. Uzun maratonlarda bilişsel rezervleri korumak en akıllıca stratejidir.");
            return getRandom(optimalLoadPool, random);
        }
    }

    private String getCoachingInsight(int totalInterruptions, int sessionCount, Random random) {
        if (sessionCount == 0)
            return "";

        double interruptionRatio = (double) totalInterruptions / sessionCount;

        if (interruptionRatio > 2.0) {
            List<String> highDistractionPool = List.of(
                    "Bugün çok fazla bağlam değişimi (görevler ve uygulamalar arası geçiş) yaşadın. Yarın telefonunu uzaklaştırıp STRICT modda çalış.",
                    "Sık sık bölünen dikkatin prefrontal kapasitene (karar verme merkezine) yük bindirdi. Dijital uyaranları izole edecek bir ortam kurgula.",
                    "Kesintilerin bu kadar yüksek olması nöral yorgunluğu artırır. Bir sonraki seansında bildirimleri tamamen kapatmanı öneririm.",
                    "Bağlam değişiminin (dikkat dağılmasının) görünmez faturasını ödedin. Bölünmüş dikkat, bilişsel kaynakların en sinsi katilidir.",
                    "Kognitif yükün kesintiler nedeniyle çok dağıldı. Zihnini çevresel tetikleyicilerden profesyonelce izole etmeyi hedefle.");
            return getRandom(highDistractionPool, random);
        } else if (totalInterruptions == 0) {
            List<String> laserFocusPool = List.of(
                    "Sıfır kesintiyle harika bir disiplin sergiledin. Bu bilişsel izolasyon mimarisini aynen korumalısın.",
                    "Hiçbir dış uyaranın prefrontal işlemlerini bölmesine izin vermedin. 'Akış' (Flow) haline girmek için mükemmel bir pratik.",
                    "Bağlam değişimi (görevler arası zorunlu geçiş) maliyeti yaşamadığın kusursuz bir nöro-verimlilik sergiledin.",
                    "Zihninin duvarlarını dış etkenlere karşı sımsıkı ördün. Bu odaklanma gücü büyük başarıların sağlam temelidir.",
                    "Derin çalışma (Deep Work) konseptini hakkıyla uygulayan, dış uyarılara kapalı pürüzsüz bir seans disiplini yakaladın.");
            return getRandom(laserFocusPool, random);
        } else {
            List<String> moderateFocusPool = List.of(
                    "Zaman zaman odaktan kopsan da nörolojik esnekliğin (hızla toparlanma becerin) sayesinde tekrar çalışmaya döndün.",
                    "Bölünmeler yaşasan bile prefrontal korteksin (bilinçli kontrol merkezin) ipleri tekrar eline aldı. Uyaranları daha hızlı elemeye çalış.",
                    "Bağlam değişimi (odak sapması) yaşandı ancak toparladın. Bu küçük kesintileri de azaltırsan zihinsel hızın katlanarak artar.",
                    "Zihinsel reflekslerin o küçük uyarılara kısaca takılsa bile, ana yörüngene dönmeyi bildiğin dirençli bir görüntü sergiledin.",
                    "Bilişsel akıştan bazen çıksan da zihinsel direksiyonun kontrolünü kaybetmedin; yine de kesinti yaratmamak her zaman daha kolaydır.");
            return getRandom(moderateFocusPool, random);
        }
    }
}
