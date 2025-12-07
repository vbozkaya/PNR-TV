# Veri Eksikliği Kontrol Rehberi

## 📊 Otomatik Veri Doğrulama

Projeye **DataValidationHelper** eklendi. Bu araç, API'den gelen verilerin eksikliklerini otomatik olarak tespit eder ve detaylı raporlar oluşturur.

## 🔍 Nasıl Kontrol Edilir?

### 1. Logcat'te Kontrol

Uygulama çalışırken, veri güncellemesi yapıldığında Logcat'te şu şekilde raporlar görünecek:

```
═══════════════════════════════════════
📊 VERİ DOĞRULAMA RAPORU: Movies
═══════════════════════════════════════
📦 Toplam kayıt: 1500
⚠️  Eksik field'ı olan kayıt: 450

🔍 EKSİK FIELD DETAYLARI:

📈 FIELD BAZINDA EKSİKLİK İSTATİSTİKLERİ:
   • plot: 800 / 1500 (%53)
   • rating: 600 / 1500 (%40)
   • tmdb: 500 / 1500 (%33)
   • streamIconUrl: 200 / 1500 (%13)
   • containerExtension: 150 / 1500 (%10)

📋 İLK 10 EKSİK KAYIT ÖRNEĞİ:
   1. Film Adı 1
      Eksik: plot, rating, tmdb
   2. Film Adı 2
      Eksik: streamIconUrl, containerExtension
   ...
═══════════════════════════════════════
```

### 2. Android Studio Logcat Filtreleme

Logcat'te şu filtreleri kullanabilirsiniz:

- **Veri doğrulama raporları**: `VERİ DOĞRULAMA RAPORU`
- **Eksik field'lar**: `EKSİK`
- **İstatistikler**: `FIELD BAZINDA`

### 3. Hangi Veriler Kontrol Ediliyor?

#### Filmler (Movies)
- ✅ **Kritik**: `streamId`, `name`, `streamIconUrl`
- ⚠️ **Önemli**: `rating`, `plot`, `categoryId`, `tmdb`, `containerExtension`

#### Diziler (Series)
- ✅ **Kritik**: `seriesId`, `name`, `coverUrl`
- ⚠️ **Önemli**: `rating`, `plot`, `categoryId`, `tmdb`, `releaseDate`

#### Canlı Yayınlar (LiveStreams)
- ✅ **Kritik**: `streamId`, `name`
- ⚠️ **Önemli**: `streamIconUrl`, `categoryId`

## 🛠️ API Response'ları Detaylı İnceleme

### HttpLoggingInterceptor

Projede zaten `HttpLoggingInterceptor` aktif. Debug modda API response'larının tamamı loglanıyor.

**Kontrol etmek için:**
1. Android Studio'da Logcat'i açın
2. Filtre: `OkHttp` veya `API`
3. Response body'leri göreceksiniz

### Logcat'te API Response'ları

```
D/OkHttp: --> GET https://example.com/player_api.php?action=get_vod_streams
D/OkHttp: <-- 200 OK (1234ms)
D/OkHttp: [
  {
    "stream_id": 123,
    "name": "Film Adı",
    "stream_icon": null,  // ← Eksik!
    "rating": null,       // ← Eksik!
    ...
  }
]
```

## 📝 Manuel Kontrol

### 1. Repository Kodlarında

`MovieRepository.kt`, `SeriesRepository.kt`, `LiveStreamRepository.kt` dosyalarında:

```kotlin
// Veri doğrulama - eksik field'ları kontrol et
val validationReport = DataValidationHelper.validateMovies(moviesDto)
validationReport.logReport()
```

Bu kod otomatik olarak çalışıyor.

### 2. DTO Dosyalarında

`app/src/main/java/com/pnr/tv/network/dto/` klasöründeki DTO dosyalarını kontrol edin:

- `MovieDto.kt` - Film verileri
- `SeriesDto.kt` - Dizi verileri
- `LiveStreamDto.kt` - Canlı yayın verileri

Hangi field'ların nullable olduğunu görebilirsiniz.

## 🎯 En Çok Eksik Olan Field'ları Bulma

Kod içinde şu şekilde kullanabilirsiniz:

```kotlin
val report = DataValidationHelper.validateMovies(moviesDto)
val mostMissing = report.getMostMissingFields(limit = 5)
// En çok eksik olan 5 field'ı döndürür
```

## 🔧 Sorun Giderme

### Eksik Veri Çok Fazlaysa

1. **API sağlayıcısıyla iletişime geçin**
   - Hangi field'ların eksik olduğunu rapor edin
   - API dokümantasyonunu kontrol edin

2. **Fallback mekanizmaları ekleyin**
   - TMDB'den eksik verileri tamamlayın (zaten var)
   - Varsayılan değerler kullanın

3. **Kullanıcıya bilgi verin**
   - Eksik verileri UI'da gösterin
   - "Veri eksik" gibi uyarılar ekleyin

### Logcat'te Rapor Görünmüyorsa

1. **Timber logging aktif mi?**
   - Debug modda çalıştığınızdan emin olun
   - `BuildConfig.ENABLE_LOGGING` kontrol edin

2. **Veri güncellemesi yapıldı mı?**
   - Ana ekranda "Güncelle" butonuna basın
   - Veya uygulamayı yeniden başlatın

## 📊 Örnek Kullanım Senaryoları

### Senaryo 1: Filmlerde Plot Eksik

```
📈 FIELD BAZINDA EKSİKLİK İSTATİSTİKLERİ:
   • plot: 800 / 1500 (%53)
```

**Çözüm**: TMDB'den plot bilgisi çekilmeye çalışılır (zaten yapılıyor).

### Senaryo 2: Container Extension Eksik

```
   • containerExtension: 150 / 1500 (%10)
```

**Çözüm**: Varsayılan olarak `.ts` kullanılır (zaten yapılıyor).

### Senaryo 3: TMDB ID Eksik

```
   • tmdb: 500 / 1500 (%33)
```

**Çözüm**: TMDB'den arama yapılarak ID bulunmaya çalışılır (zaten yapılıyor).

## 🚀 Sonraki Adımlar

1. **Raporları düzenli kontrol edin**
2. **Eksik veri yüzdelerini takip edin**
3. **API sağlayıcısıyla iletişime geçin**
4. **Fallback mekanizmalarını güçlendirin**

## 📞 Yardım

Sorun devam ederse:
- Logcat çıktılarını paylaşın
- Hangi field'ların eksik olduğunu belirtin
- API sağlayıcısının dokümantasyonunu kontrol edin

