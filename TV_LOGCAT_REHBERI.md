# TV'den Logcat Alma Rehberi

## Sorun
Uygulama TV'de açılmıyor. Hata loglarını görmek için TV'den logcat almak gerekiyor.

## Yöntem 1: ADB ile TV'ye Bağlanma

### Adım 1: TV'de Developer Options'ı Aktif Et
1. TV Ayarları → Cihaz Tercihleri → Hakkında
2. "Yapı Numarası"na 7 kez tıklayın (Developer Options aktif olur)
3. Geri dönün → Developer Options
4. "USB Debugging" veya "ADB Debugging" aktif edin
5. "Network Debugging" aktif edin (WiFi üzerinden bağlanmak için)

### Adım 2: TV'nin IP Adresini Bulun
1. TV Ayarları → Ağ → WiFi → Bağlı ağ → IP adresini not edin
2. Örnek: `192.168.1.100`

### Adım 3: Bilgisayardan TV'ye Bağlanın
```bash
# TV'nin IP adresini kullanarak bağlan
adb connect 192.168.1.100:5555

# Bağlantıyı kontrol et
adb devices
```

### Adım 4: Logcat Alın
```bash
# Tüm logları göster
adb logcat

# Sadece uygulama loglarını göster
adb logcat | grep -i "pnr\|error\|exception\|crash\|fatal"

# Logları dosyaya kaydet
adb logcat > tv_logcat.txt

# Uygulama başlatılırken logları al
adb logcat -c  # Önce logları temizle
adb logcat | grep -i "pnr\|error\|exception\|crash\|fatal" > crash_log.txt
# Şimdi TV'de uygulamayı açın
```

## Yöntem 2: Android Studio ile

1. Android Studio'yu açın
2. TV'yi USB ile bağlayın veya ADB ile bağlayın
3. View → Tool Windows → Logcat
4. Filter: `package:com.pnr.tv` veya `tag:PNR`
5. Uygulamayı TV'de açın
6. Logcat'te hataları görün

## Yöntem 3: Firebase Crashlytics

1. Firebase Console → Crashlytics → Issues
2. En son crash'i açın
3. Stack trace'i inceleyin
4. Custom keys'leri kontrol edin

## Önemli Log Filtreleri

```bash
# Sadece hatalar
adb logcat *:E

# PNR TV uygulaması logları
adb logcat | grep "com.pnr.tv"

# Crash ve exception'lar
adb logcat | grep -i "exception\|error\|crash\|fatal"

# ViewModel hataları
adb logcat | grep -i "viewmodel\|factory"

# Hilt hataları
adb logcat | grep -i "hilt\|dagger"
```

## Hata Bulunduğunda

1. Logcat çıktısını kaydedin
2. Stack trace'i kopyalayın
3. Firebase Crashlytics'te kontrol edin
4. Hata mesajını paylaşın

## Notlar

- TV'de Developer Options aktif olmalı
- TV ve bilgisayar aynı WiFi ağında olmalı
- Firewall ADB bağlantısını engellememeli
- Bazı TV'lerde ADB portu farklı olabilir (5555 yerine başka port)

