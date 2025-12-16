# Firebase Crashlytics Sınıflandırma Rehberi

## 1. Kod İçinde Sınıflandırma (Önerilen)

### Mevcut Yapılandırma

Şu anda `PnrTvApplication.kt` dosyasında temel custom keys var:
- `app_version`
- `version_code`
- `build_type`
- `package_name`
- `crash_thread_name`

### İyileştirme Önerileri

#### A. Application Seviyesinde Global Keys

`PnrTvApplication.kt` dosyasına eklenebilir:

```kotlin
private fun initializeCrashlytics() {
    val crashlytics = FirebaseCrashlytics.getInstance()
    
    val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    
    // Mevcut custom key'ler
    try {
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE)
        crashlytics.setCustomKey("build_type", if (isDebug) "debug" else "release")
        crashlytics.setCustomKey("package_name", packageName)
        
        // YENİ: Ek sınıflandırma bilgileri
        crashlytics.setCustomKey("device_model", android.os.Build.MODEL)
        crashlytics.setCustomKey("android_version", android.os.Build.VERSION.SDK_INT)
        crashlytics.setCustomKey("device_manufacturer", android.os.Build.MANUFACTURER)
        
    } catch (e: Exception) {
        Timber.e(e, "Crashlytics custom key'ler ayarlanırken hata oluştu")
    }
    
    // ... mevcut kod
}
```

#### B. Repository Seviyesinde Hata Kategorileri

`BaseContentRepository.kt` veya `ErrorHelper.kt` dosyasına eklenebilir:

```kotlin
// ErrorHelper.kt içine ekle
fun recordErrorToCrashlytics(
    exception: Throwable,
    errorCategory: String,
    context: String? = null
) {
    val crashlytics = FirebaseCrashlytics.getInstance()
    
    // Hata kategorisi
    crashlytics.setCustomKey("error_category", errorCategory)
    
    // Hata oluştuğu context (hangi ekran/fragment)
    context?.let {
        crashlytics.setCustomKey("error_context", it)
    }
    
    // Exception tipi
    crashlytics.setCustomKey("exception_type", exception.javaClass.simpleName)
    
    // Network durumu
    crashlytics.setCustomKey("is_online", NetworkUtils.isOnline(context))
    
    // Exception'ı kaydet
    crashlytics.recordException(exception)
    
    // Log mesajı
    crashlytics.log("Error in $errorCategory: ${exception.message}")
}

// Kullanım örnekleri:
// ErrorHelper.kt içinde
fun createHttpError(...): Result.Error {
    recordErrorToCrashlytics(exception, "network_error", "BaseContentRepository")
    // ... mevcut kod
}

fun createNetworkError(...): Result.Error {
    recordErrorToCrashlytics(exception, "connection_error", "BaseContentRepository")
    // ... mevcut kod
}
```

#### C. Activity/Fragment Seviyesinde Ekran Bilgisi

Her Activity/Fragment'te:

```kotlin
// BaseActivity.kt veya her Activity'de
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Crashlytics'e mevcut ekranı bildir
    FirebaseCrashlytics.getInstance().setCustomKey("current_screen", this::class.java.simpleName)
}

// Fragment'lerde
override fun onResume() {
    super.onResume()
    FirebaseCrashlytics.getInstance().setCustomKey("current_screen", this::class.java.simpleName)
}
```

#### D. UserRepository Seviyesinde Kullanıcı Bilgisi

`UserRepository.kt` veya `SessionManager.kt` içinde:

```kotlin
// Kullanıcı değiştiğinde
suspend fun setCurrentUser(user: UserAccountEntity) {
    // ... mevcut kod
    
    // Crashlytics'e kullanıcı bilgisi ekle (PII olmadan)
    val crashlytics = FirebaseCrashlytics.getInstance()
    crashlytics.setCustomKey("has_user", true)
    crashlytics.setCustomKey("user_account_id", user.id)
    // Kullanıcı adı veya şifre EKLENMEMELİ (gizlilik)
}
```

### Hata Kategorileri

Önerilen kategoriler:

```kotlin
object ErrorCategory {
    const val NETWORK_ERROR = "network_error"
    const val CONNECTION_ERROR = "connection_error"
    const val TIMEOUT_ERROR = "timeout_error"
    const val HTTP_ERROR = "http_error"
    const val DATABASE_ERROR = "database_error"
    const val UI_ERROR = "ui_error"
    const val PLAYER_ERROR = "player_error"
    const val UNKNOWN_ERROR = "unknown_error"
}
```

---

## 2. Firebase Console'da Manuel Sınıflandırma

### A. Issue Gruplama

Firebase Console → Crashlytics → Issues bölümünde:

1. **Otomatik Gruplama:**
   - Firebase, benzer stack trace'lere sahip crash'leri otomatik gruplar
   - Her grup bir "Issue" olarak görünür

2. **Manuel Gruplama:**
   - Benzer issue'ları birleştirebilirsiniz
   - "Merge issues" özelliğini kullanın

### B. Issue Etiketleme

Her issue için:

1. **Priority (Öncelik):**
   - Critical: Uygulama kullanılamaz hale getiren hatalar
   - High: Önemli özelliklerde hata
   - Medium: Küçük özelliklerde hata
   - Low: Nadir görülen veya edge case hatalar

2. **Status (Durum):**
   - Open: Yeni veya çözülmemiş
   - In Progress: Üzerinde çalışılıyor
   - Resolved: Çözüldü
   - Ignored: Bilinçli olarak göz ardı ediliyor

3. **Tags (Etiketler):**
   - `network` - Ağ hataları
   - `ui` - UI hataları
   - `player` - Video oynatıcı hataları
   - `database` - Veritabanı hataları
   - `authentication` - Kimlik doğrulama hataları

### C. Filtreleme ve Arama

Firebase Console'da:

1. **Custom Keys ile Filtreleme:**
   - `error_category` = "network_error"
   - `current_screen` = "MainActivity"
   - `app_version` = "1.0"

2. **Tarih Aralığı:**
   - Son 7 gün
   - Son 30 gün
   - Özel tarih aralığı

3. **Cihaz Bilgileri:**
   - Android versiyonu
   - Cihaz modeli
   - Uygulama versiyonu

---

## 3. Önerilen Uygulama Stratejisi

### Kombine Yaklaşım (En İyi)

**Kod İçinde:**
- Otomatik sınıflandırma için custom keys ekle
- Hata kategorileri ekle
- Context bilgisi ekle

**Firebase Console'da:**
- Issue'ları priority'ye göre sırala
- Status takibi yap
- Tags ekle
- Release notları ile ilişkilendir

### Öncelik Sırası

1. **Kritik Hatalar (Critical):**
   - Uygulama başlatılamıyor
   - Ana özellikler çalışmıyor
   - Veri kaybı riski

2. **Yüksek Öncelik (High):**
   - Önemli özelliklerde hata
   - Çok kullanıcıyı etkileyen hatalar

3. **Orta Öncelik (Medium):**
   - Küçük özelliklerde hata
   - Nadir görülen hatalar

4. **Düşük Öncelik (Low):**
   - Edge case'ler
   - Cosmetic hatalar

---

## 4. Pratik Örnekler

### Örnek 1: Network Hatası

```kotlin
catch (e: HttpException) {
    val crashlytics = FirebaseCrashlytics.getInstance()
    crashlytics.setCustomKey("error_category", "network_error")
    crashlytics.setCustomKey("http_status_code", e.code())
    crashlytics.setCustomKey("error_context", "BaseContentRepository.safeApiCall")
    crashlytics.recordException(e)
    crashlytics.log("HTTP Error: ${e.code()} - ${e.message()}")
    // ... hata işleme
}
```

### Örnek 2: Database Hatası

```kotlin
catch (e: Exception) {
    val crashlytics = FirebaseCrashlytics.getInstance()
    crashlytics.setCustomKey("error_category", "database_error")
    crashlytics.setCustomKey("error_context", "UserRepository.insertUser")
    crashlytics.recordException(e)
    // ... hata işleme
}
```

### Örnek 3: Player Hatası

```kotlin
catch (e: Exception) {
    val crashlytics = FirebaseCrashlytics.getInstance()
    crashlytics.setCustomKey("error_category", "player_error")
    crashlytics.setCustomKey("video_url", videoUrl) // Hassas bilgi değilse
    crashlytics.setCustomKey("error_context", "PlayerActivity.playVideo")
    crashlytics.recordException(e)
    // ... hata işleme
}
```

---

## 5. Firebase Console'da İşlemler

### Issue Yönetimi

1. **Issue Listesi:**
   - Firebase Console → Crashlytics → Issues
   - Tüm crash'ler burada gruplanmış olarak görünür

2. **Issue Detayı:**
   - Her issue'ya tıklayarak detayları görüntüle
   - Stack trace, custom keys, log'lar
   - Etkilenen kullanıcı sayısı
   - İlk ve son görülme tarihi

3. **Issue Düzenleme:**
   - Priority değiştir
   - Status değiştir
   - Tags ekle
   - Notlar ekle

4. **Issue Filtreleme:**
   - Priority'ye göre
   - Status'e göre
   - Tag'lere göre
   - Custom key'lere göre

---

## 6. Best Practices

### ✅ Yapılması Gerekenler

1. **Custom Keys Kullan:**
   - Hata kategorisi
   - Context bilgisi
   - Kullanıcı durumu (PII olmadan)

2. **Anlamlı Log Mesajları:**
   - Hata oluştuğu yeri belirt
   - Önemli değişken değerlerini logla

3. **Firebase Console'da Takip:**
   - Düzenli olarak kontrol et
   - Priority'leri güncelle
   - Çözülen issue'ları "Resolved" yap

### ❌ Yapılmaması Gerekenler

1. **PII (Personally Identifiable Information) Eklemeyin:**
   - Kullanıcı adı
   - Şifre
   - E-posta
   - IP adresi (gerekli değilse)

2. **Çok Fazla Custom Key:**
   - Maksimum 64 custom key
   - Sadece önemli bilgileri ekle

3. **Sensitive Data:**
   - API key'ler
   - Token'lar
   - Kişisel bilgiler

---

## 7. Özet

**Kod İçinde (Otomatik):**
- Custom keys ekle
- Hata kategorileri ekle
- Context bilgisi ekle
- Log mesajları ekle

**Firebase Console'da (Manuel):**
- Issue'ları priority'ye göre sırala
- Status takibi yap
- Tags ekle
- Notlar ekle

**Kombine Yaklaşım:**
- Kod içinde otomatik sınıflandırma
- Firebase Console'da manuel yönetim
- Her ikisini birlikte kullan

---

## Sonuç

**En iyi yaklaşım:** Her ikisini de kullan!

1. **Kod içinde** otomatik sınıflandırma yap (custom keys, kategoriler)
2. **Firebase Console'da** manuel yönetim yap (priority, status, tags)

Bu şekilde hem otomatik hem manuel kontrol sağlamış olursunuz.

