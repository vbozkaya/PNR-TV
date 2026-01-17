# Changelog

Tüm önemli değişiklikler bu dosyada belgelenecektir.

Format [Keep a Changelog](https://keepachangelog.com/tr/1.0.0/) standardına göre,
ve bu proje [Semantic Versioning](https://semver.org/lang/tr/) kullanır.

## [Unreleased]

---

## [1.3.3] - 2025-01-XX

### Düzeltmeler
- String kalite kontrolü ve düzeltmeleri
  - `error_network_generic` string'inde parametre uyumsuzluğu düzeltildi (5 dilde)
  - XML escape karakterleri doğrulandı
  - Runtime crash riski giderildi

### Teknik İyileştirmeler
- String kaynak kalite kontrolü scripti eklendi
- Tüm dillerde format string parametre uyumluluğu sağlandı

---

## [1.3.0] - 2025-01-XX

### Yeni Özellikler
- Ödüllü reklam (Rewarded Ad) entegrasyonu eklendi
- Premium olmayan kullanıcılar için adult filtresi geçici erişim özelliği
  - Kullanıcılar ödüllü reklam izleyerek 15 dakikalık adult filtresi erişimi kazanabilir
  - Geçici erişim süresi dolduğunda otomatik kapanma mekanizması
  - Focus yönetimi ve kullanıcı deneyimi iyileştirmeleri

### Teknik İyileştirmeler
- `RewardedAdManager` sınıfı eklendi (Google Mobile Ads SDK entegrasyonu)
- `AdultContentPreferenceManager` güncellendi (geçici erişim desteği)
- `UIConstants` güncellendi (Rewarded Ad Unit ID eklendi)
- SettingsActivity'de reklam akışı ve diyalog yönetimi eklendi

---

## [1.0.30] - 2025-01-XX

### Değişiklikler
- Versiyon kodu güncellemesi (Google Play Console uyumluluğu için - versionCode 30)

---

## [1.0.25] - 2025-01-XX

### Değişiklikler
- Versiyon kodu güncellemesi (Google Play Console uyumluluğu için - versionCode 25)

---

## [1.0.19] - 2025-01-XX

### Değişiklikler
- Versiyon kodu güncellemesi (Google Play Console uyumluluğu için)

---

## [1.0.18] - 2025-01-XX

### Değişiklikler
- Target SDK 35'e yükseltildi (Google Play Console gereksinimi)
- Compile SDK 35'e yükseltildi
- Versiyon güncellemesi

---

## [1.0.17] - 2025-01-XX

### Değişiklikler
- Versiyon güncellemesi

---

## [1.0.16] - 2025-01-XX

### Değişiklikler
- Versiyon güncellemesi

---

## [1.0.15] - 2025-01-XX

### İyileştirildi
- **UX:** Grid içeriklerinde aşağı yön tuşu ile gezinirken ses efekti eklendi - Artık tüm yönlerde (yukarı, aşağı, sol, sağ) aynı ses efekti çalıyor

---

## [1.0.14] - 2025-01-XX

---

## [1.0.5] - 2025-01-XX

### İyileştirildi
- **Performans:** Fragment lifecycle optimizasyonu - `ContentBrowseFragment` içindeki `onResume()` metodunda gereksiz `refreshCategoriesOnly()` çağrısı kaldırıldı
- **Performans:** Kategori sayım optimizasyonu - `buildCategories()` metodunda tüm içerikleri çekmek yerine veritabanı seviyesinde `COUNT` sorgusu kullanılıyor
- **UX:** Kategori geçişlerinde yanıp sönme (flickering) sorunu çözüldü - `StateFlow` ile önceki veri korunuyor, boş liste görünümü önleniyor
- **UX:** Kullanıcı dostu yükleme mesajları eklendi - Veri yoksa "İçerikleriniz hazırlanıyor, lütfen bekleyin..." mesajı gösteriliyor (tüm dillerde)

### Teknik İyileştirmeler
- **DAO Seviyesi:** `MovieDao` ve `SeriesDao`'ya `getCategoryCounts()` metodu eklendi
- **Repository Seviyesi:** `MovieRepository` ve `SeriesRepository`'ye count metodları eklendi
- **ViewModel Seviyesi:** `MovieViewModel` ve `SeriesViewModel`'de `StateFlow` ile önceki veri korunması eklendi
- **BaseContentViewModel:** `buildCategories()` metodu optimize edildi, kategori sayıları için COUNT sorgusu kullanılıyor
- **Reaktif Yapı:** Room Database ve Flow yapısına güvenilir, gereksiz manuel yenileme çağrıları kaldırıldı

### Çoklu Dil Desteği
- **Yeni String Resource:** `msg_preparing_content` tüm dil dosyalarına eklendi
  - Türkçe: "İçerikleriniz hazırlanıyor, lütfen bekleyin..."
  - İngilizce: "Preparing your content, please wait..."
  - İspanyolca, Fransızca, Hintçe, Endonezce, Portekizce çevirileri eklendi

### Düzeltildi
- Kategori geçişlerinde listenin anlık boş görünmesi sorunu çözüldü
- Detay sayfasından geri dönüşlerde gereksiz API çağrıları önleniyor
- Fragment lifecycle'ına uygun veri yükleme stratejisi uygulandı

---

## [1.0.4] - 2025-12-25

### Düzeltildi
- **Kritik:** "Canvas: trying to draw too large bitmap" hatası düzeltildi
- Tüm görsel yükleme yerlerine maksimum boyut limitleri (1280x720) eklendi
- `precision(Precision.EXACT)` tüm görsel yüklemelerine eklendi
- RecyclerView içindeki ImageView'larda çok büyük bitmap yüklenmesi engellendi
- Placeholder görselleri için de boyut limitleri ve precision ayarları eklendi
- BackgroundManager ile tutarlılık sağlandı (tüm görseller 1280x720 limiti ile yükleniyor)

### Teknik İyileştirmeler
- `loadContentImage`, `loadCardImage`, `loadPosterImage` fonksiyonları güncellendi
- `calculateCardSize` fonksiyonuna maksimum boyut kontrolü eklendi
- CardPresenter'daki placeholder yüklemeleri optimize edildi
- Tüm görsel yüklemelerinde `allowHardware(true)` ve `allowRgb565(true)` aktif

---

## [1.0.0] - 2024-01-XX

### Eklendi
- Android TV için IPTV uygulaması
- Film (VOD) desteği
- Dizi desteği
- Canlı yayın desteği
- Kullanıcı yönetimi
- İzleyici profilleri
- Favori kanallar
- Son izlenenler
- Oynatma pozisyonu kaydetme
- TMDB entegrasyonu (film/dizi detayları)
- Sıralama ve filtreleme özellikleri
- Offline çalışma desteği
- Crashlytics entegrasyonu
- Analytics entegrasyonu

### Teknik Özellikler
- MVVM mimarisi
- Hilt (Dependency Injection)
- Room Database
- Retrofit (Network)
- ExoPlayer (Media Player)
- Coil (Image Loading)
- Kotlin Coroutines & Flow
- Android TV Leanback Library

---

## Versiyonlama Notları

### [Major.Minor.Patch]

- **Major:** Breaking changes (geriye dönük uyumsuzluk)
- **Minor:** Yeni özellikler (geriye dönük uyumlu)
- **Patch:** Hata düzeltmeleri (geriye dönük uyumlu)

### Örnekler

- `1.0.0` → `2.0.0`: Breaking change
- `1.0.0` → `1.1.0`: Yeni özellik
- `1.0.0` → `1.0.1`: Hata düzeltmesi

---

## Gelecek Sürümler

### [1.1.0] - Planlanan

#### Eklenecek
- [ ] Arama özelliği
- [ ] Bildirimler
- [ ] Widget desteği
- [ ] Chromecast desteği
- [ ] Çoklu dil desteği

#### İyileştirilecek
- [ ] Performans optimizasyonları
- [ ] UI/UX iyileştirmeleri
- [ ] Hata yönetimi
- [ ] Offline mode iyileştirmeleri

### [1.2.0] - Planlanan

#### Eklenecek
- [ ] Parental controls
- [ ] Watchlist
- [ ] Recommendations
- [ ] Social features

---

## Notlar

- Tüm tarihler YYYY-MM-DD formatındadır
- "Unreleased" bölümü henüz yayınlanmamış değişiklikleri içerir
- Her sürüm için "Eklendi", "Değiştirildi", "Kaldırıldı", "Düzeltildi" bölümleri kullanılır

---

## Katkıda Bulunma

Changelog'a katkıda bulunmak için:

1. Değişikliklerinizi "Unreleased" bölümüne ekleyin
2. Yeni sürüm yayınlandığında "Unreleased" bölümünü sürüm numarası ile güncelleyin
3. Tarih ekleyin
4. Yeni "Unreleased" bölümü oluşturun

---

## Referanslar

- [Keep a Changelog](https://keepachangelog.com/)
- [Semantic Versioning](https://semver.org/)

