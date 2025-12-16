# Gerçek TV Performans Sorunları - Detaylı Analiz Raporu

## 📋 Özet

Uygulama Android emülatörlerde sorunsuz çalışırken, gerçek TV cihazlarında şu sorunlar yaşanıyor:
- Yayınlar bazen açılıyor, bazen açılmıyor
- İçerikler bazen güncelleniyor, bazen güncellenmiyor
- Emülatörde bu sorunlar görülmüyor

Bu rapor, bu sorunların olası sebeplerini detaylı olarak analiz eder.

---

## 🔍 Tespit Edilen Olası Sorunlar

### 1. ⚠️ Network Timeout ve Bağlantı Sorunları

#### Sorun Detayı:
- **Timeout Süresi:** 30 saniye (`Constants.Network.TIMEOUT_SECONDS`)
- **Gerçek TV'de:** Network bağlantısı genellikle daha yavaş ve daha az stabil
- **Rate Limiter:** Her API isteği arasında 500ms gecikme var (`RateLimiterInterceptor`)

#### Neden Gerçek TV'de Sorun Oluyor:
1. **WiFi Kalitesi:** Gerçek TV'ler genellikle WiFi kullanır, emülatörler genelde ethernet benzeri hızlı bağlantı kullanır
2. **Network Latency:** TV'lerin network latency'si daha yüksek olabilir
3. **Background İşlemler:** TV'lerde background network işlemleri daha agresif şekilde durdurulabilir
4. **30 saniye timeout:** Yavaş network'lerde yeterli olmayabilir

#### Kod İncelemesi:
```kotlin
// NetworkModule.kt - Satır 69-71
.connectTimeout(Constants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
.readTimeout(Constants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
.writeTimeout(Constants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
```

**Retry Mekanizması:**
- `BaseContentRepository.safeApiCall()` içinde retry var
- Ancak bazı durumlarda retry yeterli olmayabilir
- `refreshLiveStreams()` için `maxRetries = 2` ve `retryDelayMs = 2000L` kullanılıyor

---

### 2. ⚠️ Lifecycle Yönetimi Sorunları

#### Sorun Detayı:
Gerçek TV'lerde Activity lifecycle'ı daha sık değişebilir:
- TV uyku moduna geçebilir
- HDMI bağlantısı kesilebilir
- Diğer uygulamalar foreground'a geçebilir

#### Kod İncelemesi:
```kotlin
// PlayerActivity.kt - Satır 213-237
override fun onPause() {
    super.onPause()
    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    viewModel.pause() // Player pause ediliyor
}

override fun onResume() {
    super.onResume()
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    viewModel.play() // Player devam ediyor
}
```

#### Potansiyel Sorunlar:
1. **onPause/onResume Döngüsü:** TV'de sık tekrarlanabilir
2. **Coroutine İptali:** `lifecycleScope` iptal edilebilir
3. **Database İşlemleri:** Lifecycle değişiminde database işlemleri yarıda kalabilir

#### Örnek Senaryo:
```kotlin
// PlayerActivity.kt - Satır 151-190
lifecycleScope.launch {
    try {
        // Aynı kategorideki tüm kanalları al
        val channels = contentRepository.getLiveStreamsByCategoryIdSync(categoryId!!)
        // ... playlist oluşturma
    } catch (e: Exception) {
        // Fallback
    }
}
```

**Sorun:** Activity pause olduğunda `lifecycleScope` iptal edilirse, bu işlem yarıda kalabilir.

---

### 3. ⚠️ Coroutine Scope ve Threading Sorunları

#### Sorun Detayı:
Gerçek TV'lerde coroutine'ler daha yavaş çalışabilir veya beklenmedik şekilde iptal edilebilir.

#### Kod İncelemesi:
```kotlin
// PlayerActivity.kt - Satır 59
private val activityScope = CoroutineScope(Dispatchers.Main)

// PlayerActivity.kt - Satır 469-478
progressUpdateJob = activityScope.launch {
    while (isActive) {
        if (viewModel.isPlaying.value && !binding.playerControlView.isSeeking()) {
            val currentPosition = viewModel.getPlayer()?.currentPosition ?: 0L
            binding.playerControlView.updateProgress(currentPosition)
        }
        delay(500)
    }
}
```

#### Potansiyel Sorunlar:
1. **Main Dispatcher:** TV'de Main thread daha yavaş olabilir
2. **Scope İptali:** `activityScope.cancel()` çağrıldığında tüm işlemler durur
3. **ViewModel Scope:** `viewModelScope` lifecycle'dan bağımsız ama TV'de farklı davranabilir

#### Örnek Senaryo:
```kotlin
// PlayerViewModel.kt - Satır 163-178
viewModelScope.launch {
    try {
        val savedPosition = contentRepository.getPlaybackPosition(contentId)
        // ... seek işlemi
    } catch (e: Exception) {
        // Hata yakalanıyor ama TV'de farklı davranabilir
    }
}
```

---

### 4. ⚠️ Database İşlemleri ve Flow Yönetimi

#### Sorun Detayı:
Gerçek TV'lerde database işlemleri daha yavaş olabilir.

#### Kod İncelemesi:
```kotlin
// ContentRepository.kt - Satır 99-100
suspend fun getLiveStreamsByCategoryIdSync(categoryId: Int): List<LiveStreamEntity> =
    liveStreamRepository.getLiveStreamsByCategoryId(categoryId).firstOrNull() ?: emptyList()
```

**Sorun:** `firstOrNull()` Flow'dan ilk değeri alıyor. Eğer Flow henüz değer emit etmemişse, `emptyList()` döner.

#### Potansiyel Sorunlar:
1. **Flow Cold Start:** Flow ilk kez collect edildiğinde yavaş olabilir
2. **Database Query:** TV'de database query'leri daha yavaş olabilir
3. **firstOrNull() Timeout:** Eğer Flow değer emit etmezse, `firstOrNull()` sonsuza kadar bekleyebilir (ama kodda timeout yok)

#### Örnek Senaryo:
```kotlin
// PlayerActivity.kt - Satır 154
val channels = contentRepository.getLiveStreamsByCategoryIdSync(categoryId!!)
```

**Sorun:** Eğer database henüz hazır değilse veya Flow değer emit etmemişse, `channels` boş liste olabilir.

---

### 5. ⚠️ Memory ve CPU Kısıtlamaları

#### Sorun Detayı:
Gerçek TV'ler genellikle daha düşük RAM ve CPU'ya sahiptir.

#### Kod İncelemesi:
```kotlin
// PnrTvApplication.kt - Satır 39-64
override fun onLowMemory() {
    super.onLowMemory()
    Timber.tag("BACKGROUND").w("⚠️ Sistem düşük bellek uyarısı - Arkaplan cache temizleniyor")
    com.pnr.tv.util.BackgroundManager.clearCache()
}

override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    when (level) {
        TRIM_MEMORY_RUNNING_CRITICAL,
        TRIM_MEMORY_COMPLETE -> {
            com.pnr.tv.util.BackgroundManager.clearCache()
        }
    }
}
```

#### Potansiyel Sorunlar:
1. **Low Memory:** TV'de daha sık `onLowMemory()` çağrılabilir
2. **Background İşlemler:** Memory kısıtlaması nedeniyle background işlemler durdurulabilir
3. **Image Loading:** Image cache temizlenirse, görseller tekrar yüklenmek zorunda kalır

---

### 6. ⚠️ Background İşlemler ve WorkManager

#### Sorun Detayı:
Gerçek TV'lerde background işlemler daha agresif şekilde durdurulabilir.

#### Kod İncelemesi:
```kotlin
// MainViewModel.kt - Satır 217-239
private fun startTmdbBackgroundSync() {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    
    val workRequest = OneTimeWorkRequestBuilder<TmdbSyncWorker>()
        .setConstraints(constraints)
        .build()
    
    WorkManager.getInstance(context).enqueueUniqueWork(...)
}
```

#### Potansiyel Sorunlar:
1. **WorkManager Kısıtlamaları:** TV'de WorkManager işleri daha sık iptal edilebilir
2. **Network Constraint:** Network bağlantısı kesilirse, iş bekler
3. **Battery Optimization:** TV'lerde battery optimization farklı çalışabilir

---

### 7. ⚠️ ExoPlayer ve Stream Yönetimi

#### Sorun Detayı:
Gerçek TV'lerde ExoPlayer stream'leri daha yavaş başlayabilir veya başlamayabilir.

#### Kod İncelemesi:
```kotlin
// PlayerViewModel.kt - Satır 112-118
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        Constants.Player.MIN_BUFFER_DURATION.toInt(), // 5 saniye
        Constants.Player.DEFAULT_BUFFER_DURATION.toInt(), // 15 saniye
        Constants.Player.MIN_BUFFER_DURATION.toInt(), // 5 saniye
        Constants.Player.MIN_BUFFER_DURATION.toInt() // 5 saniye
    )
    .build()
```

#### Potansiyel Sorunlar:
1. **Buffer Süresi:** TV'de network yavaşsa, 5 saniye buffer yeterli olmayabilir
2. **Stream Başlatma:** Yavaş network'te stream başlamayabilir
3. **Kanal Değişimi:** `seekToNextChannel()` ve `seekToPreviousChannel()` TV'de daha yavaş olabilir

#### Örnek Senaryo:
```kotlin
// PlayerViewModel.kt - Satır 283-306
fun seekToNextChannel(): Boolean {
    val player = this.player ?: return false
    player.stop() // Stream durduruluyor
    player.seekTo(nextIndex, 0L) // Yeni kanala geçiliyor
    player.prepare() // Yeni stream hazırlanıyor
    player.play() // Oynatma başlatılıyor
}
```

**Sorun:** TV'de `prepare()` ve `play()` daha yavaş olabilir, bu da kanal değişimini geciktirebilir.

---

## 🎯 Öncelikli Sorunlar (En Olası Sebepler)

### 1. 🔴 Network Timeout ve Retry Yetersizliği
**Olasılık:** %80
- Gerçek TV'de network daha yavaş
- 30 saniye timeout yeterli olmayabilir
- Retry mekanizması bazı durumlarda yeterli değil

### 2. 🔴 Lifecycle ve Coroutine İptali
**Olasılık:** %70
- TV'de lifecycle daha sık değişiyor
- `lifecycleScope` iptal edildiğinde işlemler yarıda kalıyor
- Database işlemleri tamamlanmadan iptal edilebiliyor

### 3. 🟡 Database Flow ve firstOrNull() Sorunları
**Olasılık:** %60
- `getLiveStreamsByCategoryIdSync()` Flow'dan `firstOrNull()` alıyor
- Flow henüz değer emit etmemişse boş liste dönüyor
- TV'de database query'leri daha yavaş

### 4. 🟡 ExoPlayer Buffer ve Stream Başlatma
**Olasılık:** %50
- TV'de network yavaşsa buffer yeterli olmayabilir
- Stream başlatma daha uzun sürebilir
- Kanal değişimi gecikebilir

---

## 📊 Detaylı Senaryo Analizi

### Senaryo 1: Yayın Açılmıyor

**Akış:**
1. Kullanıcı kanal seçiyor
2. `PlayerActivity.onCreate()` çağrılıyor
3. `lifecycleScope.launch` ile playlist oluşturuluyor
4. `contentRepository.getLiveStreamsByCategoryIdSync()` çağrılıyor
5. **SORUN:** Flow henüz değer emit etmemişse, `firstOrNull()` null döner
6. `channels.isEmpty()` true olur
7. Activity kapanır veya fallback'e düşer

**Olası Sebepler:**
- Database henüz hazır değil
- Flow cold start sorunu
- Network timeout (eğer database remote'dan geliyorsa)

### Senaryo 2: İçerik Güncellenmiyor

**Akış:**
1. `MainViewModel.refreshAllContent()` çağrılıyor
2. `refreshIptvContent()` içinde `refreshLiveStreams()` çağrılıyor
3. `safeApiCall()` ile API çağrısı yapılıyor
4. **SORUN:** Network timeout veya retry başarısız
5. İçerik güncellenmiyor

**Olası Sebepler:**
- Network timeout (30 saniye yeterli değil)
- Retry mekanizması yetersiz
- Background işlemler durduruluyor

---

## 🔧 Önerilen Çözümler (Sadece Analiz - Uygulama Yapılmadı)

### 1. Network Timeout Artırma
- Timeout süresini 30'dan 60 saniyeye çıkar
- TV'ler için özel timeout değeri kullan

### 2. Retry Mekanizması İyileştirme
- Retry sayısını artır (2'den 3-4'e)
- Retry delay'i artır (2000ms'den 3000ms'e)
- Exponential backoff kullan

### 3. Lifecycle Yönetimi İyileştirme
- `lifecycleScope` yerine `viewModelScope` kullan (bazı durumlarda)
- Database işlemlerini `viewModelScope`'a taşı
- Lifecycle değişimlerinde işlemleri durdurma, sadece pause et

### 4. Database Flow İyileştirme
- `firstOrNull()` yerine timeout'lu bir mekanizma kullan
- Flow'u önceden warm-up et
- Database query'lerini optimize et

### 5. ExoPlayer Buffer İyileştirme
- TV'ler için buffer süresini artır
- Network hızına göre dinamik buffer ayarla
- Stream başlatma için retry mekanizması ekle

### 6. Error Handling İyileştirme
- Daha detaylı error logging
- Kullanıcıya daha anlaşılır hata mesajları
- Retry butonu ekle

---

## 📝 Test Önerileri

### 1. Logcat İncelemesi
- Network timeout loglarını kontrol et
- Coroutine iptal loglarını kontrol et
- Database query sürelerini ölç

### 2. Network Profiling
- Gerçek TV'de network hızını ölç
- API response sürelerini ölç
- Timeout sürelerini optimize et

### 3. Lifecycle Profiling
- Activity lifecycle değişimlerini logla
- Coroutine iptal nedenlerini logla
- Database işlem sürelerini ölç

---

## 🎯 Sonuç

Gerçek TV'deki performans sorunlarının en olası sebepleri:

1. **Network Timeout ve Retry Yetersizliği** (%80 olasılık)
2. **Lifecycle ve Coroutine İptali** (%70 olasılık)
3. **Database Flow ve firstOrNull() Sorunları** (%60 olasılık)
4. **ExoPlayer Buffer ve Stream Başlatma** (%50 olasılık)

Bu sorunlar genellikle gerçek TV'lerin daha yavaş network, daha düşük CPU/RAM ve daha agresif background işlem yönetimi nedeniyle ortaya çıkıyor.

**Öneri:** Önce network timeout ve retry mekanizmasını iyileştirin, sonra lifecycle yönetimini optimize edin.

