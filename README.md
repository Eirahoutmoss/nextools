# NexTools — Cebindeki ölçüm laboratuvarı

Nex uygulama ailesinin Android uygulaması. Telefonun sensörlerini, vericilerini ve alıcılarını ölçüm aletine dönüştürür.
Her telefona göre tasarlanır: olmayan donanımın araçları gizlenmez, **pasif** görünür ve nedeni söylenir.

> **Temel ilke:** Dürüst ölçüm. Her aracın doğruluk rozeti ve "ne ölçer / ne ölçmez" notu vardır. Kalibre edilmemiş değer "yaklaşık" yazar.

- Konsept görseli: [`docs/konsept.png`](docs/konsept.png)
- Ücretsiz; reklam ve hesap yok. Türkçe + İngilizce (telefonun diline göre; Android 13+ uygulama dili ayrıca seçilebilir).
- Çevrimdışı çalışır. İnternet yalnızca **Güncellemeleri denetle** düğmesi için kullanılır.

## İçerik

| Bölüm | Durum |
|---|---|
| Cihaz Karnesi (donanım tanıma, sensör ayrıntıları, gürültü ölçümü, PDF) | ✅ |
| Ortak araç şablonu: büyük değer + doğruluk rozeti · Kaydet ● · Dondur ❚❚ · Kalibre et ⚙ · Bilgi ⓘ | ✅ |
| Laboratuvar Defteri: kayıt, not, etiket, fotoğraf, konum (izinle), iki kaydı karşılaştırma | ✅ |
| Dışa aktarma: CSV (ham veri), JSON, PDF rapor | ✅ |
| Uygulama içi güncelleme (GitHub, SHA-256 + imza denetimi — Nex standardı) | ✅ |
| Hareket: Su Terazisi, Titreşim Ölçer, Sismograf, Makine Sağlığı, Yol Kalitesi, Sürüş Profili, Açı Ölçer, Yürüyüş Analizi, Dönüş Ölçer | ✅ |
| Manyetik: Pusula, Manyetik Alan, Metal Bulucu, Mıknatıs Gücü | ✅ |
| Basınç: Barometre, Altimetre, Kat Sayacı, Hava Eğilimi, Sızdırmazlık | ✅ |
| Işık: Lüksmetre (sensör yoksa kamerayla), Işık Titreşimi, Renk Ölçer, Renk Sıcaklığı, Spektroskop, pH / Bulanıklık | ✅ |
| Ses: Ses Ölçer, Frekans Analizörü, Akort Aleti, Ton Üreteci, Oda Yankısı (RT60), Sonar Mesafe, Metronom (aksak usuller dahil) | ✅ |
| Kamera: Büyüteç, QR / Barkod, Cetvel, Nabız, Hız Ölçer (video), Kozmik Işın Sayacı | ✅ |
| Konum: GPS Durumu, Hassas Konum, Sahte GPS Tespiti, Alan Ölçer, İz Kaydedici (GPX) | ✅ |
| Kablosuz: Wi-Fi Analizörü, Wi-Fi Mesafe (RTT), Bluetooth Tarayıcı, UWB Mesafe/Yön, NFC Okuyucu/Yazıcı, Kimlik Kartı Okuyucu, IR Kumanda | ✅ |
| Cihaz: Sensör Testi (çoklu sensör kaydı), Batarya, Ekran Testi, Hoparlör / Mikrofon Testi | ✅ |

### Kütüphaneler

- CameraX 1.3.4 ve Google Play hizmetleri kod tarayıcısı 16.1.0 (QR okuma; kamera izni istemez, Play hizmetleri gerekir). QR üretimi kütüphanesiz, `core/qr` içinde.
- JMRTD 0.7.42 + SCUBA (Android) 0.0.22 + BouncyCastle 1.78.1 (kimlik kartı çipi: PACE/BAC, imza denetimi), JP2 Android 1.0.5 (JPEG 2000 fotoğraf).
- androidx.core.uwb 1.0.0-alpha10 (UWB) ve Guava 33.3.1-android (UWB kitaplığı Guava'yı yalnızca çalışma zamanına koyduğu için CameraX'in derlenmesi için açıkça eklenir).

### Notlar

- Kimlik kartı verisi hiçbir yere yazılmaz; o ekranda ekran görüntüsü engellenir. Hoparlör / Mikrofon Testi kaydı yalnızca bellekte tutulur.
- Makine Sağlığı yalnızca ivmeölçer kullanır; makine tabanları `files/machines.json` içindedir. Nabız tıbbi cihaz değildir.
- Ekran kapalıyken arka planda kayıt yoktur (kayıt sürerken ekran açık tutulur).

## Derleme

Gerekenler: Android Studio (Ladybug veya daha yeni), JDK 17+, Android SDK 35. Kütüphane sürümleri NexYapı ile aynıdır.

```powershell
.\gradlew.bat :core:test            # çekirdek testleri (98 test)
.\gradlew.bat :app:assembleDebug    # deneme APK'sı (com.eirahoutmoss.nextools.debug — gerçek sürümle yan yana kurulur)
```

İmzalı sürüm için proje kökünde `nextools-release.jks` ve `keystore.properties` bulunmalıdır. İkisi de depoya girmez.
**Her sürüm aynı anahtarla imzalanmalıdır**; anahtar kaybolursa kullanıcılar güncelleme alamaz.

`keystore.properties` örneği:

```properties
storeFile=nextools-release.jks
storePassword=...
keyAlias=nextools
keyPassword=...
```

Anahtar üretmek için (bir kez):

```powershell
keytool -genkeypair -v -keystore nextools-release.jks -alias nextools -keyalg RSA -keysize 4096 -validity 36500
```

## Sürüm yayımlama

1. `app/build.gradle.kts` içinde `versionCode` değerini artır, `versionName` değerini değiştir.
2. Derle ve hazırla:
   ```powershell
   .\gradlew.bat clean :core:test :app:assembleRelease
   powershell -ExecutionPolicy Bypass -File tools\release\prepare-release.ps1
   ```
3. GitHub'da `Eirahoutmoss/nextools` deposunda `v<sürüm>` etiketli bir sürüm aç ve `dist\` klasöründeki `.apk` ile `.apk.sha256` dosyalarını yükle.

Uygulama taslak ve ön sürümleri yok sayar. `.sha256` dosyası olmayan, paket adı ya da imzası uyuşmayan APK kurulmaz.
Depo herkese açık olmalıdır (GitHub sürüm API'si kimlik doğrulamasız kullanılır). Depo adı `app/build.gradle.kts` içindeki `UPDATE_REPO_OWNER` / `UPDATE_REPO_NAME` alanlarındadır.

## Metinler (Türkçe + İngilizce)

Bütün ekran metinleri `tools/strings/` içindeki `strings_main.py`, `strings_tools.py` ve `strings_part*.py` dosyalarındadır. Değiştirdikten sonra:

```powershell
python tools\strings\gen_strings.py app\src\main\res app\src\main\java
```

Betik `values/strings.xml` (İngilizce, varsayılan) ve `values-tr/strings.xml` (Türkçe) dosyalarını üretir; iki dilde aynı anahtarların ve aynı biçim argümanlarının bulunduğunu, koddaki her anahtarın tanımlı olduğunu denetler.

## Proje yapısı

```
core/            Saf Kotlin: araç kataloğu, donanım → araç eşleşmesi, ölçüm hesapları
                 (eğim, pusula, metal bulucu, barometrik formül, IEC 61672 A/C/Z süzgeçleri,
                 lüks, FFT, YIN perde bulma, STA/LTA, ton sentezi, CIE Lab / CIEDE2000,
                 QR üretici, RT60 / Schroeder, ışık titreşimi, yol / sürüş / makine / nabız hesapları), CSV biçimi, güncelleme seçimi. Bilgisayarda test edilir.
app/             Android uygulaması (Kotlin + Jetpack Compose). Veritabanı yok: her kayıt
                 files/records/<id>/ altında meta.json + data.csv (+ photo.jpg).
tools/release/   Sürüm hazırlama betiği (PowerShell)
tools/strings/   TR/EN metin kaynağı ve üretici
docs/            Taslak ve konsept görseli
```

### Kayıt biçimi (CSV)

- `#` ile başlayan ilk satırlar açıklamadır (araç, cihaz, sensör, kalibrasyon, başlangıç zamanı).
- İlk sütun `t_ms`: kaydın başlangıcından geçen süre (ms). Ayırıcı virgül, ondalık ayırıcı nokta (dilden bağımsız).

### Araç kimlikleri (yalnızca geliştirici için)

Her aracın değişmez bir anahtarı vardır (`level`, `compass`, `sound_level` …); kayıt dosyalarında ve ayarlarda geçer, değiştirilmez.
Tam liste: `core/src/main/kotlin/com/eirahoutmoss/nextools/core/catalog/ToolCatalog.kt`.
