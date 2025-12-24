# Cursor AI Prompt: Kullanıcı Giriş Doğrulama Sistemi Entegrasyonu (Xtream Codes API)

**Proje Bağlamı:**
PNR TV adlı Android projesi üzerinde çalışıyoruz. Proje Kotlin, MVVM mimarisi, Hilt Dependency Injection, Room Database ve Coroutines kullanmaktadır. Şu anki yapıda kullanıcı ekleme ekranında (`AddUserActivity`) "Kaydet" butonuna basıldığında veriler doğrulanmadan direkt yerel veritabanına kaydediliyor.

**Görev:**
Kullanıcı ekleme akışına bir "Sunucu Doğrulama" (Login Verification) katmanı eklemeni istiyorum. Kullanıcı bilgileri (DNS, Kullanıcı Adı, Şifre) girilip "Kaydet"e basıldığında, uygulama önce girilen DNS adresine bir API isteği atarak bilgilerin doğruluğunu kontrol etmeli, eğer sunucu onayı gelirse veritabanına kaydetmelidir.

**Teknik Gereksinimler ve Adım Adım Talimatlar:**

### 1. Bağımlılıkların Eklenmesi (`build.gradle`)
Eğer projede yoksa, `app/build.gradle` dosyasına Retrofit ve Gson Converter bağımlılıklarını ekle.

### 2. Veri Modellerinin Oluşturulması (Data Layer)
Xtream Codes API yanıtını karşılayacak data class'ları oluştur. Genellikle yanıt şöyledir:
`player_api.php?username=x&password=y`
Dönen JSON yapısı için şu modelleri ekle:
- `XtreamLoginResponse`: İçinde `user_info` (object) ve `server_info` (object) barındırır.
- `UserInfo`: `username`, `password`, `status`, `exp_date` gibi alanlar.
- `ServerInfo`: `url`, `port`, `server_protocol` alanları.

### 3. API Arayüzünün Tanımlanması (Network Layer)
`XtreamApiService` adında bir interface oluştur.
- Endpoint: `@GET("player_api.php")`
- Parametreler: `username` ve `password`.
- **Önemli:** Base URL dinamik olacağı için Retrofit kurulumunda veya çağrı anında dinamik URL yapısını dikkate al (veya OkHttp interceptor ile host değiştirme mantığı kur, ancak en basiti `@Url` annotasyonu kullanmak olabilir, fakat Xtream standart endpoint'i sabit olduğu için dinamik Base URL yönetimi gerekecek).

### 4. Network Modülü ve Hilt (DI)
`NetworkModule` adında bir Hilt modülü oluştur.
- **Unsafe SSL Desteği:** IPTV sunucularının çoğu SSL sertifikasına sahip değildir veya geçersiz sertifika kullanır. Bu yüzden `OkHttpClient` oluştururken SSL doğrulamasını devre dışı bırakan (Unsafe/TrustAllCerts) bir yapı kurmalısın. Aksi takdirde `SSLHandshakeException` alırız.
- Retrofit instance'ını sağlamalı.

### 5. Repository Katmanı
`AuthRepository` adında yeni bir repository (veya `ContentRepository` içine) oluştur.
- Fonksiyon: `suspend fun verifyUser(dns: String, username: String, password: String): Result<Boolean>`
- Bu fonksiyon, girilen DNS adresini base URL olarak alıp API isteği atmalıdır.
- HTTP 200 dönerse ve JSON içinde `user_info` doluysa `Success` dönmeli.
- Hata durumunda `Failure` dönmeli.

### 6. ViewModel Güncellemesi (`AddUserViewModel`)
`saveUser` fonksiyonunu güncelle:
1. `_isLoading` adında bir `StateFlow` ekle ve UI'ya yükleniyor durumunu bildir.
2. "Kaydet" tetiklendiğinde önce `_isLoading.value = true` yap.
3. Repository üzerinden `verifyUser` fonksiyonunu çağır.
4. **Eğer Doğrulama Başarılıysa:** Mevcut kayıt mantığını (Room insert) çalıştır.
5. **Eğer Doğrulama Başarısızsa:** UI'ya hata mesajı gönder (Event veya State üzerinden) ve veritabanına kayıt yapma.
6. İşlem bitince `_isLoading.value = false` yap.

### 7. UI Güncellemesi (`AddUserActivity`)
- Layout (`activity_add_user.xml`) dosyasına bir `ProgressBar` ekle (başlangıçta gizli).
- ViewModel'deki `isLoading` durumunu dinle. Yükleniyorken butonları pasif yap ve ProgressBar'ı göster.
- Hata durumunda kullanıcıya "Giriş bilgileri hatalı veya sunucuya erişilemiyor" şeklinde Toast veya Snackbar göster.

**Özet:**
Kodun temiz, modüler ve mevcut mimariye (Hilt/MVVM) uygun olmalı. Lütfen bu adımları sırasıyla uygula ve gerekli dosyaları oluştur/düzenle.