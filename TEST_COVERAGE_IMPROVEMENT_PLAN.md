# Test Coverage Artırma Planı

## 📊 Mevcut Durum (Güncellendi: 2024)

- **Toplam Test Sayısı**: 365 test
- **Başarılı Testler**: 365 (%100 başarı oranı)
- **Başarısız Testler**: 0
- **Atlanan Testler**: 60 (@Ignore ile işaretli)
- **Mevcut Coverage**: ~60% (hedef: %70+)
- **Test Durumu**: ✅ Tüm çalışan testler başarılı

## 🎯 Öncelikli Test Alanları

### 1. ✅ Tamamlanan
- [x] SeriesDetailViewModel testleri düzeltildi
- [x] Derleme hataları giderildi

### 2. ✅ Tamamlanan (Güncellendi)
- [x] SharedViewModel için error handling testleri eklendi
- [x] Flow test'lerinde timeout sorunları düzeltildi
- [x] MovieViewModel edge case testleri eklendi
- [x] SeriesViewModel edge case testleri eklendi
- [x] LiveStreamViewModel kategori filtreleme testleri eklendi
- [x] ContentRepository error handling testleri düzeltildi

### 3. 📋 Yapılacaklar

#### A. ViewModel Testleri (Yüksek Öncelik) ✅ TAMAMLANDI
- [x] **SharedViewModel**
  - [x] refreshAllContent temel testleri
  - [x] Error handling testleri (timeout sorunları düzeltildi)
  - [x] fetchUserInfo edge cases
  - [x] State management testleri
  
- [ ] **MovieViewModel**
  - [x] Temel testler mevcut
  - [x] Arama fonksiyonları testleri (edge cases dahil)
  - [x] Sıralama testleri (tüm SortOrder tipleri)
  - [x] Kategori filtreleme edge cases
  
- [ ] **SeriesViewModel**
  - [x] Temel testler mevcut
  - [x] Arama fonksiyonları testleri (edge cases dahil)
  - [x] Sıralama testleri (tüm SortOrder tipleri)
  - [x] Kategori filtreleme edge cases

- [ ] **LiveStreamViewModel**
  - [x] Temel testler mevcut
  - [x] Kategori filtreleme testleri (edge cases dahil)
  - [x] Arama testleri (LiveStreamViewModel'de arama fonksiyonu yok, sadece kategori filtreleme var)

#### B. Repository Testleri (Orta Öncelik) ⚠️ EKSİK: ViewerRepository
- [x] **ContentRepository**
  - [x] Temel testler mevcut
  - [x] Error handling testleri (ConnectivityManager mock eklendi)
  - [x] Network error senaryoları
  - [x] Empty data senaryoları

- [x] **TmdbRepository**
  - [x] Temel testler mevcut
  - [x] Cache expiration testleri
  - [x] API error handling
  - [x] Locale helper entegrasyon testleri (not: LocaleHelper bağımlılığı nedeniyle bazı testler integration test gerektirir)

- [ ] **ViewerRepository** ❌ EKSİK
  - [ ] Test dosyası oluşturulmalı
  - [ ] addViewer, deleteViewer, getAllViewers testleri
  - [ ] getViewerById, getViewerIdsWithFavorites testleri

#### C. Utility Testleri (Düşük Öncelik) ⚠️ EKSİK: NetworkUtils, SecureLogger
- [x] ErrorHelper testleri mevcut
- [x] DataValidationHelper testleri mevcut
- [x] LocaleHelper testleri mevcut
- [x] IntentValidator testleri mevcut
- [x] ViewerInitializer testleri mevcut
- [x] BackgroundManager testleri mevcut
- [x] CategoryNameHelper testleri mevcut
- [ ] NetworkUtils testleri ❌ EKSİK
- [ ] SecureLogger testleri ❌ EKSİK
- [ ] SortPreferenceManager testleri ⚠️ @Ignore (DataStore bağımlılığı)

#### D. Integration Testleri (Orta Öncelik) ❌ EKSİK
- [ ] Database entegrasyon testleri (In-memory Room database)
- [ ] API entegrasyon testleri (MockWebServer ile)
- [ ] Repository + ViewModel entegrasyon testleri

#### E. DAO Testleri (Yüksek Öncelik) ❌ EKSİK - HİÇ TEST YOK
- [ ] MovieDao testleri
- [ ] SeriesDao testleri
- [ ] LiveStreamDao testleri
- [ ] MovieCategoryDao testleri
- [ ] SeriesCategoryDao testleri
- [ ] LiveStreamCategoryDao testleri
- [ ] FavoriteDao testleri
- [ ] RecentlyWatchedDao testleri
- [ ] PlaybackPositionDao testleri
- [ ] UserDao testleri
- [ ] ViewerDao testleri
- [ ] TmdbCacheDao testleri
- [ ] WatchedEpisodeDao testleri

#### F. Worker Testleri (Orta Öncelik) ❌ EKSİK
- [ ] TmdbSyncWorker testleri (WorkManager testing library gerekli)

## 🛠️ Test Ekleme Stratejisi

### 1. Error Handling Testleri
Her ViewModel ve Repository için:
- Network error senaryoları
- Empty data senaryoları
- Invalid data senaryoları
- Exception handling

### 2. Edge Case Testleri
- Null/empty input'lar
- Boundary değerler
- Concurrent operations
- State transitions

### 3. Integration Testleri
- MockWebServer ile API testleri
- In-memory database testleri
- ViewModel + Repository entegrasyonu

## 📈 Coverage Hedefleri

| Kategori | Mevcut | Hedef | Öncelik | Durum |
|----------|--------|-------|---------|-------|
| ViewModels | ~85% | 80% | Yüksek | ✅ Hedef aşıldı |
| Repositories | ~90% | 75% | Yüksek | ✅ Hedef aşıldı (ViewerRepository eksik) |
| Utilities | ~80% | 80% | Orta | ✅ Hedef aşıldı (NetworkUtils, SecureLogger eksik) |
| Use Cases | ~100% | 70% | Orta | ✅ Hedef aşıldı |
| DAOs | 0% | 70% | Yüksek | ❌ Test yok |
| Workers | 0% | 60% | Orta | ❌ Test yok |
| **Toplam** | **~60%** | **70%** | - | ⚠️ DAO ve Worker testleri eklendiğinde hedefe ulaşılacak |

## 🚀 Hızlı Başlangıç

### Adım 1: SharedViewModel Testlerini Düzelt
```kotlin
// Flow test'lerinde timeout ekle
viewModel.updateState.test(timeout = 5.seconds) {
    // Test logic
}
```

### Adım 2: Error Handling Testleri Ekle
Her ViewModel için:
- Network error testleri
- Empty data testleri
- Exception handling testleri

### Adım 3: Edge Case Testleri
- Null input testleri
- Boundary value testleri
- State transition testleri

## 📝 Test Yazma İpuçları

1. **AAA Pattern Kullan**: Arrange, Act, Assert
2. **Descriptive Test Names**: `should_doSomething_when_condition`
3. **Mock'ları Doğru Kullan**: Suspend fonksiyonlar için `runBlocking`
4. **Flow Testleri**: Turbine kullan, timeout ekle
5. **Error Scenarios**: Her success path için bir error path test et

## 🔍 Test Coverage Raporu

Coverage raporunu görüntülemek için:
```bash
./gradlew jacocoTestReport
```

Rapor: `app/build/reports/jacoco/jacocoTestReport/html/index.html`

## ✅ Checklist

- [x] Mevcut testler çalışıyor (365 test, %100 başarı)
- [x] Derleme hataları düzeltildi
- [x] SharedViewModel error handling testleri
- [x] ViewModel edge case testleri (MovieViewModel, SeriesViewModel, LiveStreamViewModel)
- [x] Repository error handling testleri (ContentRepository, TmdbRepository)
- [ ] ViewerRepository testleri ❌ EKSİK
- [ ] DAO testleri ❌ EKSİK (13 DAO için test yok)
- [ ] Worker testleri ❌ EKSİK (TmdbSyncWorker)
- [ ] NetworkUtils testleri ❌ EKSİK
- [ ] SecureLogger testleri ❌ EKSİK
- [ ] Integration testleri (MockWebServer, in-memory database)
- [ ] @Ignore testlerini düzelt (Robolectric/WorkManager testing library)
- [ ] Coverage %70+ hedefine ulaşıldı (şu an ~60%, DAO ve Worker testleri eklendiğinde hedefe ulaşılacak)

## 📋 Detaylı Test Durumu

Detaylı test durumu ve eksik test alanları için `TEST_DOCUMENTATION.md` dosyasına bakın.

