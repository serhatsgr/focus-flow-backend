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

    public String generateFullAnalysisText(double averageScore, double morningScore, double afternoonScore,
            int morningCount,
            int afternoonCount, int totalDurationMinutes, int totalInterruptions, int sessionCount, long seed) {
        Random seededRandom = new Random(seed);

        String temporalText = getTemporalInsight(morningScore, afternoonScore, morningCount, afternoonCount,
                seededRandom);
        String volumeText = getVolumeInsight(totalDurationMinutes, averageScore, seededRandom);
        String coachingText = getCoachingInsight(totalInterruptions, sessionCount, averageScore, seededRandom);

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
        List<String> onlyMorningProductivePool = List.of(
                "Bugün derin çalışma bloklarını erken bitirdin. Günün geri kalanında zihnine toparlanma (recovery) izni verebilirsin.",
                "Odak eforunu yalnızca sabah saatlerine topladın. Öğleden sonrayı bilişsel rezervlerini (zihinsel enerjini) yenilemek için kullanıyorsun.",
                "Günün erken bölümünde yüksek verim gösterdin, zihinsel deponu erkenden tüketmek sirkadiyen ritminle uyumlu bir strateji olabilir.");

        List<String> onlyMorningUnproductivePool = List.of(
                "Sabah saatlerinde masaya oturma iradesi göstersen de, prefrontal kapasiten (odak merkezin) bu seanslarda çok dağınık ve isteksizdi.",
                "Zihinsel enerjini sadece günün ilk yarısında harcadın ama skorlardan anladığımız kadarıyla 'derin çalışma' bölgesine ulaşılamamış.",
                "Günün açılışını yapsan da bilişsel akış zayıftı. Masada geçen süre her zaman odaklı geçen süre anlamına gelmiyor.");

        List<String> onlyAfternoonProductivePool = List.of(
                "Güne geç başlasan da öğleden sonraki bloklarda hiper-odak evresine girmeyi başardın.",
                "Bilişsel uyanışın günün ikinci yarısında gerçekleşti, akşamüstüne doğru nöronal ağların (bilgi işlem yolların) tam kapasiteye ulaştı.",
                "Sabah saatlerini es geçip tüm zihinsel yükü günün ikinci yarısına bıraktın ve sirkadiyen ritminle uyumlu olarak başarıyla yönettin.");

        List<String> onlyAfternoonUnproductivePool = List.of(
                "Günün ikinci yarısında masaya oturmayı başarsan da, prefrontal korteksin (odak merkezin) bu efora yanıt verememiş. Süreyi doldurmak derin çalışmak anlamına gelmez.",
                "Oturumlarını günün sonlarına bıraktın ama zihinsel enerji çok düşüktü; kognitif bir direnç (zihinsel isteksizlik) durumu söz konusu.",
                "Öğleden sonraki oturumların skorları zayıf. Nöronal yolların yavaş çalışıyor, zihnen göreve bir türlü 'giriş' yapamamışsın.");

        if (morningCount > 0 && afternoonCount == 0) {
            return getRandom(morningScore >= 60 ? onlyMorningProductivePool : onlyMorningUnproductivePool, random);
        } else if (morningCount == 0 && afternoonCount > 0) {
            return getRandom(afternoonScore >= 60 ? onlyAfternoonProductivePool : onlyAfternoonUnproductivePool,
                    random);
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

    private String getVolumeInsight(int totalDurationMinutes, double averageScore, Random random) {
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
            if (averageScore < 60) {
                List<String> strugglingVolumePool = List.of(
                        "Masada geçirdiğin süre makul olsa da zihnen orada değildin. Nöronal ağların rölantide kalmış, kognitif bir direnç (zihinsel isteksizlik) yaşamışsın.",
                        "Fiziksel oturma süren optimal görünse de, odak kaliten dipsiz kuyu gibiydi. Zihnin görevden inanılmaz derecede koptu.",
                        "Görünürde başarılı bir mesai saati, ama zihinsel çıktın asgari düzeyde. Zamanın hakkını veren derin bir çalışma yapamamışsın.");
                return getRandom(strugglingVolumePool, random);
            }
            List<String> optimalLoadPool = List.of(
                    "Zihinsel kapasiteni (kognitif eşiğini) aşırı zorlamadan, sürdürülebilir bir günlük efor sarf ettin.",
                    "Nöro-verimlilik açısından ideal hacimde bir gündü. Bilişsel sınırlarını koruyarak dengeli çalıştın.",
                    "Oturum sürelerin tam kararında; nöronal ağlarını (bilgi yollarını) tükenmişliğe sürüklemeden verimli sonuçlar elde ettin.",
                    "Altın oranda bir performans gösterdin; ne prefrontal yorgunluk hissedecek kadar aşırı, ne de potansiyelini harcayacak kadar az.",
                    "Tam olması gerektiği kadar bir nöral efor sergiledin. Uzun maratonlarda bilişsel rezervleri korumak en akıllıca stratejidir.");
            return getRandom(optimalLoadPool, random);
        }
    }

    private String getCoachingInsight(int totalInterruptions, int sessionCount, double averageScore, Random random) {
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
        } else if (averageScore < 60) {
            List<String> daydreamingPool = List.of(
                    "Dış dünyadan görünürde bir kesinti almasan da, içsel odaklanma kaliten (zihinsel akışın) oldukça düşüktü. Yarın STRICT modun acımasız disiplinine ihtiyacın var.",
                    "Bağlam değişimi yaşamadın ama bilişsel skorların dibe vurdu. Masada oturan fiziksel bedenin ile, hayallere dalan zihnin arasındaki makası acilen kapatmalısın.",
                    "Telefonuna uzanmadın ama zihnin sürekli geçmişe veya geleceğe savruldu. Gizli dikkat yitimini (internal distraction) kesmezsen verimin artmaz.");
            return getRandom(daydreamingPool, random);
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
