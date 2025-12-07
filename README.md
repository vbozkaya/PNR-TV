# PNR TV Projesi

## 1. Projenin Amacı

PNR TV, sadece Android TV platformu için geliştirilmiş bir IPTV (İnternet Protokolü Televizyonu) uygulamasıdır. Kullanıcıların canlı yayınları, filmleri ve dizileri izlemesine olanak tanır.

Bu proje, mobil cihazları hedeflemez ve tamamen TV kullanıcı deneyimine odaklanmıştır.

## 2. Kullanılan Teknolojiler ve Mimari

- **Dil:** Kotlin
- **Platform:** Android TV
- **Mimari Yaklaşım:** Model-View-ViewModel (MVVM) hedeflenmektedir. Şu anki yapı, bu mimariye geçiş için temel oluşturur.
- **Ana Bileşenler:**
  - **`BrowseSupportFragment`:** Android TV için standart ana ekran ve kategori tarama arayüzünü sağlar.
  - **`FragmentActivity`:** Tema uyumluluğu ve modern Android geliştirme pratiği için temel aktivite sınıfı olarak kullanılır.

## 3. Proje Yapısı

Projenin ana kodları `app/src/main/java/com/pnr/tv` klasörü altında yer almaktadır.

- `**/MainActivity.kt` : Uygulamanın ana giriş noktasıdır. Sadece `MainFragment`'i barındıran bir konteynerdir.
- `**/BaseActivity.kt` : Tüm aktiviteler için ortak ayarları (tam ekran gibi) içeren temel sınıftır.
- `**/MainFragment.kt` : Uygulamanın ana ekranını yöneten, kategorileri ve içerikleri listeleyen ana fragment'tir.
- `**/ui/browse/CardPresenter.kt`: İçerik kartlarının görünümünü tanımlar. ContentItem interface'ini kullanarak tüm içerik tipleri (MovieEntity, SeriesEntity, LiveStreamEntity) için ortak bir yapı sağlar.

## 4. Özellikler

### 4.1. Sıralama ve Filtreleme
- Filmler ve diziler için 6 farklı sıralama seçeneği
- Kullanıcı tercihinin kalıcı saklanması (DataStore)
- Tüm kategorilere otomatik sıralama uygulama
- Detaylı dokümantasyon: `SIRALAMA_FILTRELEME_DOKUMANTASYONU.md`

## 4. Kurulum ve Çalıştırma

1. Projeyi Android Studio'da açın.
2. Gerekli Gradle senkronizasyonunun tamamlanmasını bekleyin.
3. Projeyi bir Android TV emülatöründe veya fiziksel bir Android TV cihazında çalıştırın.
