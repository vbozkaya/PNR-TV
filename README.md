# PNR TV Projesi

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-TV-green.svg)](https://developer.android.com/tv)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

PNR TV, sadece Android TV platformu için geliştirilmiş bir IPTV (İnternet Protokolü Televizyonu) uygulamasıdır. Kullanıcıların canlı yayınları, filmleri ve dizileri izlemesine olanak tanır.

Bu proje, mobil cihazları hedeflemez ve tamamen TV kullanıcı deneyimine odaklanmıştır.

## 📋 İçindekiler

- [Özellikler](#-özellikler)
- [Teknolojiler](#-teknolojiler)
- [Kurulum](#-kurulum)
- [Dokümantasyon](#-dokümantasyon)
- [Katkıda Bulunma](#-katkıda-bulunma)
- [Telif Hakkı ve Sorumluluk Reddi](#️-telif-hakkı-ve-sorumluluk-reddi)
- [Lisans](#-lisans)

## ✨ Özellikler

### İçerik Yönetimi
- 🎬 **Filmler (VOD):** Film kategorileri, detaylar ve oynatma
- 📺 **Diziler:** Dizi kategorileri, sezonlar, bölümler ve oynatma
- 📡 **Canlı Yayınlar:** Canlı TV kanalları ve kategorileri
- 🔍 **TMDB Entegrasyonu:** Film ve dizi detayları için The Movie Database desteği

### Kullanıcı Özellikleri
- 👤 **Çoklu Kullanıcı:** Birden fazla IPTV hesabı yönetimi
- 🎭 **İzleyici Profilleri:** Her kullanıcı için ayrı izleyici profilleri
- ⭐ **Favoriler:** Favori kanalları kaydetme
- 📚 **Son İzlenenler:** Son izlenen içerikleri takip etme
- ⏯️ **Oynatma Pozisyonu:** Kaldığınız yerden devam etme

### Diğer Özellikler
- 🔄 **Offline Desteği:** Yerel veritabanı ile offline çalışma
- 🎨 **Modern UI:** Android TV Leanback Library ile optimize edilmiş arayüz
- 🔐 **Güvenlik:** Network security config ile özel sertifika desteği
- 📊 **Analytics:** Firebase Analytics ve Crashlytics entegrasyonu

## 🛠️ Teknolojiler

### Dil ve Platform
- **Dil:** Kotlin 1.9.22
- **Platform:** Android TV
- **Min SDK:** 21 (Android 5.0 Lollipop)
- **Target SDK:** 34 (Android 14)

### Mimari
- **Mimari Desen:** MVVM (Model-View-ViewModel)
- **Dependency Injection:** Hilt (Dagger)
- **Reactive Programming:** Kotlin Coroutines & Flow

### Kütüphaneler
- **UI:** AndroidX Leanback, Material Design
- **Database:** Room
- **Network:** Retrofit, OkHttp, Moshi
- **Media:** ExoPlayer (Media3)
- **Image Loading:** Coil
- **Analytics:** Firebase Analytics, Crashlytics
- **Background Tasks:** WorkManager

## 🚀 Kurulum

Detaylı kurulum rehberi için [SETUP_GUIDE.md](SETUP_GUIDE.md) dosyasına bakın.

### Hızlı Başlangıç

1. **Gereksinimler:**
   - Android Studio Hedgehog (2023.1.1) veya üzeri
   - JDK 21
   - Android SDK 34

2. **Projeyi Klonlama:**
   ```bash
   git clone <repository-url>
   cd "PNR TV"
Yapılandırma:

local.properties dosyası oluşturun

TMDB_API_KEY ekleyin (detaylar için SETUP_GUIDE.md)

Çalıştırma:

Android Studio'da projeyi açın

Gradle senkronizasyonunu bekleyin

Android TV emülatöründe veya fiziksel cihazda çalıştırın

📚 Dokümantasyon
Proje dokümantasyonu:

API_DOCUMENTATION.md - API endpoint'leri ve request/response formatları

ARCHITECTURE.md - Mimari yapı, bileşenler ve veri akışı

SETUP_GUIDE.md - Detaylı kurulum ve yapılandırma rehberi

CONTRIBUTING.md - Katkıda bulunma rehberi

CHANGELOG.md - Sürüm geçmişi ve değişiklikler

Proje Yapısı
bash
Kodu kopyala
app/src/main/java/com/pnr/tv/
├── db/              # Room Database (Entities, DAOs)
├── di/              # Hilt Modules (Dependency Injection)
├── domain/          # Use Cases, Models
├── network/         # API Services, DTOs
├── repository/      # Data Repositories
├── ui/              # UI Components (Activities, Fragments, ViewModels)
└── util/            # Utility Classes
🤝 Katkıda Bulunma
Katkıda bulunmak istiyorsanız, lütfen CONTRIBUTING.md dosyasını okuyun.

Katkı Süreci
Projeyi fork edin

Feature branch oluşturun (git checkout -b feature/AmazingFeature)

Değişikliklerinizi commit edin (git commit -m 'Add some AmazingFeature')

Branch'inizi push edin (git push origin feature/AmazingFeature)

Pull Request oluşturun

⚠️ Telif Hakkı ve Sorumluluk Reddi
PNR TV uygulaması herhangi bir canlı yayın, film, dizi veya telif hakkına tabi içerik sağlamaz.
Uygulama yalnızca kullanıcının kendi temin ettiği içerik kaynaklarını (ör. IPTV listeleri) oynatmasına olanak tanıyan bir medya oynatıcıdır.

Kullanıcının izlediği veya eriştiği içeriklerin:

Telif haklarına uygunluğu,

Yasal olup olmadığı,

Bulunduğu ülkenin mevzuatına uygunluğu

tamamen kullanıcının kendi sorumluluğundadır.

Uygulamayı geliştirenler olarak:

Kullanıcının izlediği içeriklerden,

Telif hakkı ihlallerinden,

Yasa dışı içerik kullanımından

hiçbir şekilde sorumluluk kabul edilmez.

Kullanıcılara, telif hakkı bulunan ve yasal olmayan hiçbir yayını izlememeleri açıkça tavsiye edilir.

📝 Lisans
Bu proje MIT lisansı altında lisanslanmıştır. Detaylar için LICENSE dosyasına bakın.

🙏 Teşekkürler
The Movie Database (TMDB) - Film ve dizi verileri için

Android TV Leanback Library - TV UI bileşenleri için

Tüm açık kaynak kütüphane geliştiricilerine

Not: Bu proje sadece Android TV platformu için geliştirilmiştir. Mobil cihazlarda çalışmaz.