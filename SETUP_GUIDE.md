# Setup Guide

Bu rehber, PNR TV projesini geliştirme ortamında kurmak ve çalıştırmak için gerekli adımları açıklar.

## İçindekiler

- [Gereksinimler](#gereksinimler)
- [Kurulum](#kurulum)
- [Yapılandırma](#yapılandırma)
- [Çalıştırma](#çalıştırma)
- [Sorun Giderme](#sorun-giderme)

---

## Gereksinimler

### Yazılım Gereksinimleri

1. **Android Studio**
   - Versiyon: Hedgehog (2023.1.1) veya üzeri
   - Android SDK: 34
   - JDK: 21

2. **Android SDK**
   - Minimum SDK: 21 (Android 5.0 Lollipop)
   - Target SDK: 34 (Android 14)
   - Android TV SDK: Kurulu olmalı

3. **Gradle**
   - Versiyon: 8.1.4 (proje ile birlikte gelir)
   - Gradle Wrapper kullanılır

4. **Kotlin**
   - Versiyon: 1.9.22

### Donanım Gereksinimleri

- **Geliştirme:** Android TV emülatörü veya fiziksel Android TV cihazı
- **RAM:** En az 8GB (16GB önerilir)
- **Disk:** En az 10GB boş alan

---

## Kurulum

### 1. Projeyi Klonlama

```bash
git clone <repository-url>
cd "PNR TV"
```

### 2. Android Studio'da Açma

1. Android Studio'yu açın
2. **File → Open** seçeneğini seçin
3. Proje klasörünü seçin
4. **Trust Project** butonuna tıklayın

### 3. Gradle Senkronizasyonu

Android Studio otomatik olarak Gradle senkronizasyonunu başlatır. Eğer başlamazsa:

1. **File → Sync Project with Gradle Files** seçeneğini seçin
2. Veya **Sync Now** butonuna tıklayın

**Not:** İlk senkronizasyon uzun sürebilir (5-10 dakika). Tüm bağımlılıklar indirilir.

### 4. SDK ve Araçları Kontrol Etme

1. **Tools → SDK Manager** seçeneğini açın
2. Aşağıdaki paketlerin kurulu olduğundan emin olun:
   - Android SDK Platform 34
   - Android TV SDK
   - Android SDK Build-Tools 34
   - Android Emulator
   - Android SDK Platform-Tools

---

## Yapılandırma

### 1. local.properties Dosyası

Proje kök dizininde `local.properties` dosyası oluşturun (eğer yoksa):

```properties
# Android SDK yolu (Windows)
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk

# Android SDK yolu (macOS/Linux)
# sdk.dir=/Users/YourUsername/Library/Android/sdk

# TMDB API Key (Zorunlu)
TMDB_API_KEY=your_tmdb_api_key_here
```

**Önemli:** `TMDB_API_KEY` değerini mutlaka ekleyin. API anahtarı olmadan proje derlenmez.

#### TMDB API Key Alma

1. [TMDB](https://www.themoviedb.org/) web sitesine gidin
2. Hesap oluşturun veya giriş yapın
3. **Settings → API** bölümüne gidin
4. **Request an API Key** butonuna tıklayın
5. Geliştirme için "Developer" seçeneğini seçin
6. API anahtarınızı kopyalayın ve `local.properties` dosyasına ekleyin

### 2. Google Services Yapılandırması

Firebase kullanımı için `google-services.json` dosyası gerekir:

1. Firebase Console'da proje oluşturun
2. Android uygulaması ekleyin
3. `google-services.json` dosyasını indirin
4. Dosyayı `app/` klasörüne kopyalayın

**Not:** Firebase kullanmıyorsanız, `app/build.gradle` dosyasından Google Services plugin'ini kaldırabilirsiniz.

### 3. ProGuard Yapılandırması

Release build için ProGuard kuralları `app/proguard-rules.pro` dosyasında tanımlanmıştır. Gerekirse ek kurallar ekleyebilirsiniz.

---

## Çalıştırma

### 1. Android TV Emülatörü Oluşturma

1. **Tools → Device Manager** seçeneğini açın
2. **Create Device** butonuna tıklayın
3. **TV** kategorisinden bir cihaz seçin (örn: Android TV (1080p))
4. Sistem görüntüsü seçin (API 21 veya üzeri)
5. Emülatörü oluşturun

### 2. Uygulamayı Çalıştırma

#### Yöntem 1: Android Studio'dan

1. Emülatörü başlatın veya fiziksel cihazı bağlayın
2. **Run → Run 'app'** seçeneğini seçin
3. Veya yeşil **Run** butonuna tıklayın

#### Yöntem 2: Komut Satırından

```bash
# Debug build
./gradlew installDebug

# Release build
./gradlew installRelease
```

**Windows için:**
```cmd
gradlew.bat installDebug
```

### 3. İlk Çalıştırma

Uygulama ilk açıldığında:

1. **SplashActivity** gösterilir
2. Kullanıcı kontrolü yapılır
3. Eğer kullanıcı yoksa, kullanıcı ekleme ekranına yönlendirilir
4. Kullanıcı ekledikten sonra ana ekrana geçilir

---

## Build Yapılandırmaları

### Debug Build

```bash
./gradlew assembleDebug
```

**Özellikler:**
- Logging aktif
- ProGuard kapalı
- Hata ayıklama sembolleri dahil
- Destructive database migration aktif

### Release Build

```bash
./gradlew assembleRelease
```

**Özellikler:**
- Logging kapalı
- ProGuard aktif (kod küçültme ve obfuscation)
- Hata ayıklama sembolleri kaldırılmış
- Production ayarları aktif

**Not:** Release build için signing yapılandırması gerekir. `app/build.gradle` dosyasında signing config ekleyin.

---

## Test Çalıştırma

### Unit Tests

```bash
./gradlew testDebugUnitTest
```

### Instrumented Tests

```bash
./gradlew connectedDebugAndroidTest
```

### Test Coverage

```bash
./gradlew jacocoTestReport
```

Rapor: `app/build/reports/jacoco/jacocoTestReport/html/index.html`

---

## Sorun Giderme

### 1. Gradle Senkronizasyon Hataları

**Sorun:** "Failed to sync Gradle project"

**Çözüm:**
1. **File → Invalidate Caches / Restart** seçeneğini seçin
2. `.gradle` klasörünü silin
3. Projeyi yeniden açın
4. Gradle senkronizasyonunu tekrar çalıştırın

### 2. TMDB_API_KEY Hatası

**Sorun:** "TMDB_API_KEY bulunamadı!"

**Çözüm:**
1. `local.properties` dosyasının proje kök dizininde olduğundan emin olun
2. `TMDB_API_KEY=your_key_here` satırının doğru formatta olduğundan emin olun
3. Android Studio'yu yeniden başlatın

### 3. SDK Hatası

**Sorun:** "SDK location not found"

**Çözüm:**
1. `local.properties` dosyasında `sdk.dir` yolunu kontrol edin
2. Android SDK'nın kurulu olduğundan emin olun
3. SDK Manager'dan gerekli paketleri kurun

### 4. Build Hataları

**Sorun:** "Build failed"

**Çözüm:**
1. **Build → Clean Project** seçeneğini seçin
2. **Build → Rebuild Project** seçeneğini seçin
3. Gradle cache'ini temizleyin: `./gradlew clean`
4. Android Studio'yu yeniden başlatın

### 5. Emülatör Sorunları

**Sorun:** Emülatör başlamıyor

**Çözüm:**
1. HAXM veya Hyper-V'nin kurulu olduğundan emin olun
2. BIOS'ta virtualization'ın aktif olduğundan emin olun
3. Emülatör ayarlarını kontrol edin (RAM, disk alanı)

### 6. Network Security Hatası

**Sorun:** "Network security config" hatası

**Çözüm:**
1. `app/src/main/res/xml/network_security_config.xml` dosyasını kontrol edin
2. Self-signed sertifikalar için gerekli yapılandırmaların olduğundan emin olun

### 7. Database Migration Hatası

**Sorun:** "Migration failed"

**Çözüm:**
1. DEBUG modda: Uygulamayı kaldırıp yeniden yükleyin (veri kaybı olur)
2. RELEASE modda: Migration dosyalarını kontrol edin
3. Database version'ı kontrol edin

---

## Geliştirme İpuçları

### 1. Logging

Debug build'de Timber logging aktif. Logları görmek için:

```kotlin
Timber.d("Debug message")
Timber.e("Error message")
```

### 2. Hata Ayıklama

- **Breakpoints:** Kod satırlarında breakpoint koyabilirsiniz
- **Logcat:** Android Studio'da Logcat sekmesinden logları görebilirsiniz
- **Network Inspector:** Network isteklerini gözlemleyebilirsiniz

### 3. Code Style

Proje ktlint kullanır. Kod stilini kontrol etmek için:

```bash
./gradlew ktlintCheck
```

Kod stilini düzeltmek için:

```bash
./gradlew ktlintFormat
```

### 4. Git Hooks (Opsiyonel)

Pre-commit hook ekleyebilirsiniz:

```bash
# .git/hooks/pre-commit
#!/bin/sh
./gradlew ktlintCheck
```

---

## Ortam Değişkenleri

### Android Studio

- **ANDROID_HOME:** Android SDK yolu (opsiyonel)
- **JAVA_HOME:** JDK yolu (opsiyonel)

### Gradle

- **GRADLE_OPTS:** Gradle JVM seçenekleri
- **ORG_GRADLE_DAEMON:** Gradle daemon ayarları

---

## Bağımlılıklar

Tüm bağımlılıklar `app/build.gradle` ve `gradle/libs.versions.toml` dosyalarında tanımlanmıştır.

**Ana Bağımlılıklar:**
- AndroidX libraries
- Hilt (Dependency Injection)
- Room (Database)
- Retrofit (Network)
- ExoPlayer (Media Player)
- Coil (Image Loading)
- Firebase (Analytics, Crashlytics)

---

## Sonraki Adımlar

Kurulum tamamlandıktan sonra:

1. [API_DOCUMENTATION.md](API_DOCUMENTATION.md) dosyasını okuyun
2. [ARCHITECTURE.md](ARCHITECTURE.md) dosyasını inceleyin
3. [CONTRIBUTING.md](CONTRIBUTING.md) dosyasına göz atın
4. Projeyi keşfedin ve geliştirmeye başlayın!

---

## Destek

Sorun yaşarsanız:

1. Bu dokümantasyonu kontrol edin
2. GitHub Issues'da benzer sorunları arayın
3. Yeni bir issue oluşturun

---

## Notlar

- **Windows:** PowerShell veya Command Prompt kullanabilirsiniz
- **macOS/Linux:** Terminal kullanın
- **Path:** Proje yolunda boşluk varsa tırnak içine alın: `"PNR TV"`

