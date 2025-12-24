# Cursor AI Prompt: "Kullanıcı Kaydet" Butonu Tepki Vermeme Sorunu Debug & Fix

**Durum:**
PNR TV uygulamasında `AddUserActivity` ekranında kullanıcı bilgilerini girip "Kaydet" (Save) butonuna basıldığında hiçbir aksiyon alınmıyor. UI donmuyor ama işlem de başlamıyor veya hata mesajı görünmüyor.

**Muhtemel Sebepler:**
1. **HTTPS Zorlaması:** `AuthRepository` içinde DNS adresine otomatik `https://` ekleniyor olabilir. Çoğu IPTV sağlayıcısı SSL sertifikasına sahip değildir ve sadece `http://` ile çalışır. Bu durum `SSLHandshakeException` fırlatabilir ve bu hata kullanıcıya yansıtılmıyor olabilir.
2. **Cleartext Traffic İzni:** Android 9 (Pie) ve üzeri sürümlerde varsayılan olarak `http` trafiği engellidir. `AndroidManifest.xml` içinde `android:usesCleartextTraffic="true"` eksik olabilir.
3. **Exception Yutulması:** ViewModel veya Repository katmanında `try-catch` blokları hatayı yakalıyor ama UI'a (`_errorMessage`) doğru şekilde iletmiyor olabilir.
4. **Coroutine Dispatcher:** Ağ işlemi yanlışlıkla Main Thread üzerinde çalıştırılmaya çalışılıyor olabilir (gerçi Retrofit bunu genellikle yönetir).

**Görevler:**

Lütfen aşağıdaki adımları sırasıyla incele ve gerekli düzeltmeleri yap:

### 1. `AndroidManifest.xml` Kontrolü
- `<application>` tag'i içinde `android:usesCleartextTraffic="true"` özelliğinin ekli olduğundan emin ol. Eğer yoksa ekle. Bu, HTTP (güvenli olmayan) bağlantılar için zorunludur.

### 2. `AuthRepository.kt` Düzeltmeleri
- **HTTPS Zorlamasını Kaldır:** `normalizeDns` fonksiyonunda varsayılan olarak `https` eklemek yerine, eğer protokol belirtilmemişse `http://` ekle.
- **Loglama:** `verifyUser` fonksiyonunun başına ve catch bloğuna detaylı `Timber.d` ve `Timber.e` logları ekle. Hangi aşamada takıldığını görelim.

```kotlin
// Örnek Düzeltme Mantığı
private fun normalizeDns(dns: String): String {
    var normalized = dns.trim()
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
        normalized = "http://$normalized" // Varsayılan HTTP olsun
    }
    return normalized.trimEnd('/') + "/"
}
```

### 3. `AddUserViewModel.kt` Loglama ve Hata Yönetimi
- `addUser` fonksiyonunun girişine `Timber.d("addUser tetiklendi: $username")` logu ekle.
- `_isLoading` state'inin UI tarafından algılanıp algılanmadığını kontrol etmek için log ekle.
- `catch` bloğunun hatayı gerçekten yakalayıp `_errorMessage`'a atadığından emin ol.

### 4. `AddUserActivity.kt` UI Kontrolü
- Butona tıklandığında `saveUser()` fonksiyonunun çağrılıp çağrılmadığını logla.
- `isLoading` collect edildiğinde ProgressBar'ın görünürlüğünü değiştiren kodun çalıştığını teyit et.

**Özet:**
Amacımız butona basıldığında arka planda ne olduğunu görmek ve HTTP/HTTPS uyumsuzluğunu gidermektir. Lütfen kodları bu doğrultuda güvenli bir şekilde güncelle.