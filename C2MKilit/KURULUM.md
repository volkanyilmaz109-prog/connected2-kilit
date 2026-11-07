# C2M Kilit

connected2.me açıkken alt menüdeki **Keşfet** (en sol) ve **Shuffle** (orta) sekmelerinin
üstüne siyah bir ⛔ katmanı koyar; dokunuşlar alttaki uygulamaya ulaşmaz. Uygulama bu
ekranlardan birinde açılırsa (veya Shuffle ekranı görünürse) otomatik olarak **Sohbet**
sekmesine geçirir. Shuffle'daki arama çubuğu da bu sayede kullanılamaz.

## Nasıl çalışır
- Bir **Erişilebilirlik Servisi** (AccessibilityService). Arka planda sürekli çalışır,
  telefon açılınca kendiliğinden başlar; ayrıca "uygulamayı açmanız" gerekmez.
- Katman `TYPE_ACCESSIBILITY_OVERLAY` ile çizilir: "diğer uygulamaların üzerinde göster"
  izni gerekmez, Android 12+ dokunma engellemelerine takılmaz.
- Sekmelerin yeri ekrandan okunur (5 sekmeli alt satır aranır), bulunamazsa ekran
  genişliğinin 1/5'lik dilimlerine göre tahmin edilir. Klavye açıkken tahmin yapılmaz.

## Derleme
Android Studio ile `C2MKilit` klasörünü aç → Build → Build APK(s).
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Telefonda kurulum (Xiaomi / HyperOS dahil)
1. APK'yı kur, **C2M Kilit**'i aç.
2. **Android 13+ / yeni MIUI:** Erişilebilirlik listesinde servis gri / "kısıtlı ayar" diyorsa:
   Ayarlar → Uygulamalar → C2M Kilit → sağ üst ⋮ → **Kısıtlı ayarlara izin ver**.
3. **1) Erişilebilirlik** butonu → İndirilen uygulamalar → C2M Kilit → Aç.
4. **2) Pil kısıtlamasını kaldır** → İzin ver (Xiaomi'de "Kısıtlama yok" seç).
5. **3) Otomatik başlatma** → C2M Kilit'i aç.
6. Xiaomi'de son uygulamalar ekranında C2M Kilit'i aşağı çekip **kilitle** (asma kilit).
7. connected2.me'yi aç, iki sekmenin üstünde ⛔ görmelisin.

## Paket adı tutmazsa
Varsayılan hedef `com.connected2.ozzy.c2m` ve adında `connected2` geçen her paket.
Kilit çıkmazsa: connected2.me'yi aç, C2M Kilit'e dön → "Son görülen uygulama" satırındaki
paketi **hedefe ekle**.

## Sekme sırasını değiştirmek
`BlockerService.java` içinde `BLOCKED_TABS = {0, 2}` (0 = en sol) ve `SAFE_TAB = 1`.

## Sürüm 2.0
- **Ana şifre:** İlk açılışta rastgele 16 haneli şifre üretilir, yalnızca bir kez gösterilir
  (Kopyala butonu var). Cihazda sadece şifrenin özeti (PBKDF2) saklanır. Uygulama her
  açılışta şifre ister; 5 yanlış denemeden sonra 30 sn'den başlayıp katlanarak artan bekleme.
- **Sıkı mod:** Kilitliyken ayarlarda C2M Kilit'in uygulama bilgisi, erişilebilirlik
  sayfası veya kaldırma penceresi açılırsa ana ekrana atar. Şifre girildikten sonra 10 dk
  ayarlar serbest ("Şimdi kilitle" ile erken bitirilebilir).
- **Bölünmüş ekran:** Hedef uygulama aktif pencere olmasa da tüm pencereler taranır.
- **Zıplama:** Katman yalnızca yeni konum art arda iki ölçümde aynı çıkınca taşınır.

## Güncelleme
Kodu değiştir → `versionCode`'u bir artır (app/build.gradle) → `gradlew assembleRelease`
→ `app/build/outputs/apk/release/app-release.apk`'yı telefona kur. Eski sürümün üstüne
kurulur, şifre ve ayarlar korunur. **`keystore/c2m.keystore` dosyasını kaybetme**; aynı
anahtarla imzalanmayan APK üstüne kurulamaz.

## Şifreyi kaybedersen
Telefonu **Güvenli mod**da başlat (üçüncü parti erişilebilirlik servisleri çalışmaz),
C2M Kilit'i normal şekilde kaldır.

## Sürüm 3.0 — VOCD
- **Ad ve logo:** Uygulamanın adı VOCD, logosu koyu zemin üstünde maviden yeşile geçen minimal bir V.
  Paket adı değişmedi (`com.volkan.c2mblocker`), bu yüzden eski sürümün üstüne kurulur.
- **Buton kilitleri:** Olaylar artık erteleme (debounce) yerine sınırlama (throttle) ile işleniyor;
  animasyon boyunca yenileme gecikmiyor. Kilit pencereleri bir kez eklenip sonra sadece taşınıyor
  veya gizleniyor (her seferinde ekle/kaldır yok). Konum değişince 50 ms içinde doğrulanıp yerine oturuyor.
- **Site engelleme:** Chrome, Samsung İnternet, Firefox, Opera, Edge, Brave, Vivaldi, Kiwi, Yandex,
  DuckDuckGo ve Mi Tarayıcı'nın adres çubuğu okunur. Listedeki site veya alt alan adı
  (ornek.com → m.ornek.com da) açılınca sekme google.com'a yönlenir ve "Bu site engelli" ekranı çıkar.
  Başka uygulamanın içinde açılan Chrome sekmesi (Custom Tab) doğrudan kapatılır.
- **Toplu liste:** Yapıştır, dosyadan içe aktar veya internetten indir. Desteklenen biçimler:
  satır başına alan adı/URL, hosts dosyası (`0.0.0.0 site.com`), adblock (`||site.com^`).
  Hazır listeler: StevenBlack yetişkin içerik (~77 bin) ve kumar.
- İnternet izni yalnızca liste indirmek için kullanılır.

## Sürüm 3.1
- **Kilitlerin kayması:** Android 11 ve sonrası, kilit pencerelerini durum / gezinme çubuğuna ve
  klavyeye göre kendiliğinden itiyordu; gezinme çubuğu ya da klavye açılıp kapandıkça kilit
  kayıyordu. Artık konum doğrudan ekran koordinatı olarak veriliyor. Bir ROM yine de kaydırırsa
  servis pencerenin gerçek yerini okuyup farkı düzeltiyor.
- **Tam kaplama:** Kilit artık yalnızca simgeyi değil, sekmenin tüm dilimini (komşu sekmeyle
  arasındaki boşluğun ortasına kadar) ve menü çubuğunun tüm yüksekliğini kaplıyor. Sistemin
  geri / ana ekran tuşlarının ve klavyenin üstüne taşmıyor.
- **Arama açıkken:** Arama önerileri gibi açılır pencereler ana pencerenin önüne geçince kilit
  kalkıyordu; artık uygulamanın tüm pencerelerinde menü aranıyor. Klavye açıkken menü klavyenin
  üstüne çıktıysa orada da kilitleniyor.
- **Yanıp sönme:** Menü bir an okunamayınca kilit hemen kalkmıyor, 300 ms boyunca hiç
  görünmezse kalkıyor. Taşıma ise yeni konum 60 ms sabit kalınca yapılıyor.
- **Şifre:** Yeni şifreler 8 karakter, küçük harf ve rakam (l, o, 0, 1 yok). Eski 16 haneli
  şifre çalışmaya devam eder; panelde "Yeni şifre oluştur" ile 8 haneliye geçilir.
- **Özel DNS filtresi:** Panelde "Tüm uygulamalarda filtre" kartı. Android'in Özel DNS ayarına
  aile filtreli bir sunucu (CleanBrowsing / Cloudflare) yazılınca yetişkin siteler her tarayıcıda
  ve uygulamada kapanır. Kart adresi kopyalar, ayarları açar ve filtrenin açık olup olmadığını gösterir
  (bunun için ağ durumu izni eklendi).
