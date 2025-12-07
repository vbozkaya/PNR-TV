# Logcat'te Veri Doğrulama Raporlarını Görme

## 🔍 Hızlı Filtreleme

### Android Studio Logcat'te

1. **Logcat penceresini açın** (Alt+6 veya View → Tool Windows → Logcat)

2. **Filtre kutusuna şunları yazın:**
   ```
   VERİ DOĞRULAMA
   ```
   veya
   ```
   DataValidation
   ```

3. **Log seviyesini ayarlayın:**
   - Dropdown'dan **Debug** seçin (veya **Verbose**)

4. **Tag filtresi:**
   ```
   tag:DataValidation
   ```

## 📊 Tam Raporu Görmek İçin

### Yöntem 1: Logcat Buffer Size Artırma

1. Logcat penceresinde sağ üstteki **⚙️ Settings** ikonuna tıklayın
2. **Logcat buffer size** değerini artırın (örn: 10000)
3. **Apply** tıklayın

### Yöntem 2: Logcat'i Temizleyip Yeniden Başlatma

1. Logcat'te **Clear** butonuna tıklayın
2. Uygulamada **Güncelle** butonuna basın
3. Raporlar yeniden görünecek

### Yöntem 3: Filtreleme

**Sadece rapor başlıklarını görmek için:**
```
VERİ DOĞRULAMA RAPORU
```

**Sadece istatistikleri görmek için:**
```
FIELD BAZINDA
```

**Sadece eksik kayıtları görmek için:**
```
EKSİK KAYIT
```

## 🎯 Örnek Logcat Çıktısı

Başarılı bir rapor şöyle görünür:

```
D/DataValidationReport: ═══════════════════════════════════════
D/DataValidationReport: 📊 VERİ DOĞRULAMA RAPORU: Movies
D/DataValidationReport: ═══════════════════════════════════════
D/DataValidationReport: 📦 Toplam kayıt: 1500
D/DataValidationReport: ⚠️  Eksik field'ı olan kayıt: 450
D/DataValidationReport: 
D/DataValidationReport: 📈 FIELD BAZINDA EKSİKLİK İSTATİSTİKLERİ:
D/DataValidationReport:    • plot: 800 / 1500 (%53)
D/DataValidationReport:    • rating: 600 / 1500 (%40)
D/DataValidationReport:    • tmdb: 500 / 1500 (%33)
D/DataValidationReport:    • streamIconUrl: 200 / 1500 (%13)
D/DataValidationReport:    • containerExtension: 150 / 1500 (%10)
D/DataValidationReport: 
D/DataValidationReport: 📋 İLK 5 EKSİK KAYIT ÖRNEĞİ:
D/DataValidationReport:    1. Film Adı 1 → Eksik: plot, rating, tmdb
D/DataValidationReport:    2. Film Adı 2 → Eksik: streamIconUrl, containerExtension
D/DataValidationReport:    3. Film Adı 3 → Eksik: plot
D/DataValidationReport:    4. Film Adı 4 → Eksik: rating, tmdb
D/DataValidationReport:    5. Film Adı 5 → Eksik: containerExtension
D/DataValidationReport:    ... ve 445 kayıt daha
D/DataValidationReport: 
D/DataValidationReport: 📊 ÖZET: %70 tam veri, %30 eksik veri
D/DataValidationReport: ═══════════════════════════════════════
```

## 🚨 Sorun Giderme

### Raporlar Görünmüyorsa

1. **Logcat seviyesini kontrol edin**
   - Debug veya Verbose olmalı
   - Info, Warn, Error'da görünmez

2. **Uygulama çalışıyor mu?**
   - Uygulamayı yeniden başlatın
   - Ana ekranda "Güncelle" butonuna basın

3. **Timber logging aktif mi?**
   - Debug build'de çalıştığınızdan emin olun
   - `BuildConfig.ENABLE_LOGGING` kontrol edin

### Sadece Başlık Görünüyorsa

1. **Logcat buffer size'ı artırın** (yukarıda anlatıldı)
2. **Logcat'i temizleyip yeniden başlatın**
3. **Filtreleri kaldırın** (tüm logları görmek için)

### Çok Fazla Log Varsa

1. **Sadece DataValidation tag'ini filtreleyin:**
   ```
   tag:DataValidation
   ```

2. **Sadece rapor başlıklarını görmek için:**
   ```
   VERİ DOĞRULAMA RAPORU
   ```

## 💡 İpuçları

- **Logcat'i dosyaya kaydetmek için:** Logcat penceresinde sağ tık → Save Logcat to File
- **Belirli bir zaman aralığını görmek için:** Logcat'te tarih/saat filtresi kullanın
- **Arama yapmak için:** Logcat'te Ctrl+F (veya Cmd+F Mac'te)

## 📱 ADB ile Komut Satırından

Eğer Android Studio kullanmıyorsanız:

```bash
# Sadece veri doğrulama raporlarını görmek için
adb logcat -s DataValidationReport:D

# Tüm logları görmek için
adb logcat | grep "VERİ DOĞRULAMA"

# Dosyaya kaydetmek için
adb logcat -s DataValidationReport:D > validation_report.txt
```

