# PNR TV - Geliştirme Planı

## ✅ Tamamlanan Görevler

### 1. Güvenlik İyileştirmeleri
- ✅ Network Security Configuration düzeltildi
- ✅ ProGuard kuralları optimize edildi
- ✅ Firebase Crashlytics entegre edildi

### 2. Kod Kalitesi
- ✅ MainViewModel refactoring (rate limiting ağ katmanına taşındı)
- ✅ MainFragment refactoring (kod tekrarı azaltıldı, ToolbarController interface eklendi)
- ✅ Dependency updates (tüm kütüphaneler güncellendi)

### 3. Test Altyapısı
- ✅ ViewModel testleri eklendi (MainViewModel, MovieDetailViewModel, SeriesDetailViewModel)
- ✅ Repository testleri eklendi (MovieRepository)
- ✅ Test altyapısı kuruldu (Turbine, Mockito-Kotlin)

### 4. CI/CD
- ✅ GitHub Actions workflow oluşturuldu
- ✅ Otomatik test çalıştırma
- ✅ Otomatik kod analizi (ktlint)
- ✅ Otomatik derleme (debug/release APK)

### 5. Hata Raporlama
- ✅ Firebase Crashlytics entegrasyonu
- ✅ Timber ile Crashlytics entegrasyonu

### 6. Proje Yönetimi
- ✅ GitHub repository bağlandı
- ✅ CI/CD pipeline aktif

---

## 🎯 Öncelikli Görevler

### 1. Test Kapsamını Artırma (Kritik İş Mantığı)

**Öncelik: Yüksek**

Kritik iş mantığı için unit testler eklenmeli:

#### a) RateLimiterInterceptor Testi
- İstekler arası gecikme kontrolü
- Thread safety testleri
- Edge case'ler (çok hızlı istekler, interrupt durumları)

#### b) BuildLiveStreamUrlUseCase Testi
- URL oluşturma mantığı
- Farklı input senaryoları
- Hata durumları

#### c) ErrorHelper Testi
- Hata mesajı oluşturma
- Farklı exception tipleri
- Localization kontrolü

#### d) ViewerInitializer Testi
- Viewer oluşturma mantığı
- Varsayılan viewer kontrolü

### 2. Eksik ViewModel Testleri

**Öncelik: Orta**

- ContentViewModel
- ViewerViewModel
- LiveStreamsViewModel
- PlayerViewModel

### 3. Eksik Repository Testleri

**Öncelik: Orta**

- ContentRepository (Facade pattern testleri)
- SeriesRepository
- LiveStreamRepository
- TmdbRepository
- ViewerRepository
- FavoriteRepository
- PlaybackPositionRepository
- RecentlyWatchedRepository

### 4. Use Case Testleri

**Öncelik: Orta**

- BuildLiveStreamUrlUseCase (kritik iş mantığı)

---

## 🚀 Önerilen Sonraki Adımlar

### Kısa Vadeli (1-2 Hafta)

1. **Kritik İş Mantığı Testleri** (Öncelik: Yüksek)
   - RateLimiterInterceptor testi
   - BuildLiveStreamUrlUseCase testi
   - ErrorHelper testi

2. **Eksik ViewModel Testleri** (Öncelik: Orta)
   - ContentViewModel
   - ViewerViewModel
   - LiveStreamsViewModel

3. **Eksik Repository Testleri** (Öncelik: Orta)
   - SeriesRepository
   - LiveStreamRepository
   - TmdbRepository

### Orta Vadeli (1 Ay)

1. **UI Testleri (Espresso)**
   - Ana ekran testleri
   - Video oynatma testleri
   - Kullanıcı yönetimi testleri

2. **Integration Testleri**
   - Repository + Database testleri
   - Network + Repository testleri

3. **Performance Testleri**
   - Büyük veri setleri ile test
   - Memory leak testleri

### Uzun Vadeli (2-3 Ay)

1. **Yeni Özellikler**
   - Offline mode
   - Favori içerik senkronizasyonu
   - Gelişmiş arama özellikleri

2. **Mimari İyileştirmeler**
   - Use Case pattern genişletme
   - Domain layer güçlendirme

3. **Kullanıcı Deneyimi**
   - UI/UX iyileştirmeleri
   - Animasyonlar
   - Erişilebilirlik iyileştirmeleri

---

## 📊 Test Kapsamı Hedefi

- **Mevcut**: ~40% (ViewModel ve bazı Repository testleri)
- **Hedef (Kısa Vadeli)**: ~60% (Kritik iş mantığı + eksik ViewModel/Repository testleri)
- **Hedef (Uzun Vadeli)**: ~80% (UI testleri + Integration testleri dahil)

---

## 🔧 Teknik Borçlar

1. **Kod İyileştirmeleri**
   - Deprecated API kullanımları (FLAG_FULLSCREEN, SHOW_FORCED)
   - Migration parametre isimleri (db -> database)

2. **Dokümantasyon**
   - API dokümantasyonu
   - Architecture decision records (ADR)

3. **Performans**
   - Image loading optimizasyonu
   - Database query optimizasyonu

---

## 📝 Notlar

- Tüm testler CI/CD pipeline'ında otomatik çalışıyor
- Firebase Crashlytics production hatalarını otomatik topluyor
- GitHub Actions her push'ta test ve derleme yapıyor

