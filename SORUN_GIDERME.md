# Uygulama Açılmıyor - Sorun Giderme

## Yapılan Düzeltmeler

1. ✅ **Timber sıralaması düzeltildi**: Timber önce setup ediliyor, sonra Crashlytics initialize ediliyor
2. ✅ **CrashlyticsTree güvenli hale getirildi**: Try-catch eklendi, Firebase başlatılmamışsa crash olmayacak

## Olası Sorunlar ve Çözümler

### 1. Firebase Crashlytics Başlatma Sorunu

**Belirti**: Uygulama açılmıyor, crash oluyor

**Çözüm**: 
- `google-services.json` dosyasının `app/` klasöründe olduğundan emin olun
- Package name'in `com.pnr.tv` olduğunu kontrol edin
- Firebase Console'da uygulamanın doğru package name ile kayıtlı olduğunu kontrol edin

### 2. Hilt Dependency Injection Sorunu

**Belirti**: `lateinit var workerFactory` initialize edilmemiş hatası

**Çözüm**:
- Projeyi temizleyin: `.\gradlew clean`
- Yeniden derleyin: `.\gradlew assembleDebug`

### 3. Timber Logging Sorunu

**Belirti**: Timber setup edilmeden kullanılıyor

**Çözüm**: ✅ Düzeltildi - Timber önce setup ediliyor

### 4. Network Security Config Sorunu

**Belirti**: Network istekleri başarısız

**Çözüm**: `network_security_config.xml` dosyasını kontrol edin

## Test Etme

1. **Temiz derleme yapın**:
```powershell
.\gradlew clean assembleDebug
```

2. **APK'yı yükleyin ve test edin**

3. **Logcat'i kontrol edin** (Android Studio'da):
   - Logcat'te "FATAL" veya "AndroidRuntime" hatalarını arayın
   - Firebase ile ilgili hataları kontrol edin

## Geçici Çözüm: Crashlytics'i Devre Dışı Bırakma

Eğer sorun devam ederse, Crashlytics'i geçici olarak devre dışı bırakabilirsiniz:

```kotlin
private fun initializeCrashlytics() {
    val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (isDebug) {
        // Debug modda Crashlytics'i devre dışı bırak
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        } catch (e: Exception) {
            // Firebase başlatılmamışsa sessizce devam et
        }
    }
}
```

## Hata Mesajı Paylaşın

Eğer sorun devam ederse, lütfen şu bilgileri paylaşın:
- Android Studio Logcat'teki hata mesajı
- Uygulama açılırken ne oluyor? (Crash mi, beyaz ekran mı, hiç açılmıyor mu?)
- Hangi cihazda test ediyorsunuz?

