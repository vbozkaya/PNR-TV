# Canlı Kanal Değişimi - Detaylı İmplementasyon Raporu

## 📋 Genel Bakış

Bu rapor, canlı kanallar arasında Page Up/Down tuşlarıyla geçiş yapma özelliğinin implementasyonunu detaylı olarak açıklar.

**Tarih:** 2024  
**Özellik:** Canlı kanallar arasında Page Up/Down ile geçiş  
**Durum:** ✅ Implementasyon tamamlandı, ⚠️ Test edilmesi gerekiyor

---

## 🎯 Amaç

Canlı kanal izlerken, kullanıcının Page Up/Down tuşlarıyla aynı kategorideki önceki/sonraki kanallara geçiş yapabilmesi. Player üzerinde kalarak, Activity'den çıkmadan kanal değişimi yapılması.

---

## 📝 Yapılan Değişiklikler

### 1. PlayerActivity.kt

#### 1.1. Yeni Constant Eklendi
```kotlin
companion object {
    const val EXTRA_CATEGORY_ID = "extra_category_id" // Canlı yayın kategorisi ID (kanal değişimi için)
}
```

**Konum:** Satır 35  
**Amaç:** Intent'e kategori ID'sini eklemek için kullanılıyor.

#### 1.2. Yeni Değişkenler Eklendi
```kotlin
@Inject
lateinit var contentRepository: ContentRepository

@Inject
lateinit var buildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase

private var categoryId: Int? = null // Canlı yayın kategorisi ID
```

**Konum:** Satır 42-47  
**Amaç:** 
- `contentRepository`: Aynı kategorideki kanalları almak için
- `buildLiveStreamUrlUseCase`: Yeni kanal URL'i oluşturmak için
- `categoryId`: Mevcut kanalın kategori ID'sini saklamak için

#### 1.3. Import'lar Eklendi
```kotlin
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.repository.ContentRepository
import javax.inject.Inject
```

**Konum:** Satır 16-19, 27  
**Amaç:** Gerekli sınıfları kullanabilmek için.

#### 1.4. Intent'ten categoryId Okunması
```kotlin
categoryId = intent.getIntExtra(EXTRA_CATEGORY_ID, -1).takeIf { it != -1 }
```

**Konum:** Satır 75  
**Amaç:** Intent'ten kategori ID'sini alıp saklamak.

#### 1.5. Page Up/Down Tuş Handler'ı Eklendi
```kotlin
// Page Up/Down - Canlı kanallar arasında geçiş (sadece canlı yayınlarda)
if (channelId != null && categoryId != null) {
    when (keyCode) {
        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
            // Panel açıksa önce kapat
            if (isPanelOpen) {
                if (audioAdapter != null && settingsPanelBinding.recyclerSubtitleTracks.adapter == audioAdapter) {
                    hideAudioPanel()
                } else {
                    hideSubtitlePanel()
                }
            }
            // Kanal değişimini başlat
            lifecycleScope.launch {
                changeChannel(keyCode == KeyEvent.KEYCODE_PAGE_UP)
            }
            return true // Olayı tüket
        }
    }
}
```

**Konum:** Satır 267-285  
**Amaç:** 
- Page Up/Down tuşlarını yakalamak
- Sadece canlı yayınlarda çalışmasını sağlamak (`channelId != null && categoryId != null`)
- Panel açıksa önce kapatmak
- `changeChannel()` fonksiyonunu çağırmak
- `return true` ile olayı tüketmek (PlayerControlView'a gitmemesi için)

**Önemli Not:** Bu kod `onKeyDown()` metodunun en başında, panel kontrolünden ÖNCE yer alıyor. Bu sayede:
- Panel açıkken de çalışır (paneli kapatıp kanal değiştirir)
- PlayerControlView'daki canlı yayın engellemesinden önce yakalanır

#### 1.6. changeChannel() Fonksiyonu Eklendi
```kotlin
private suspend fun changeChannel(previous: Boolean) {
    val currentChannelId = channelId ?: return
    val currentCategoryId = categoryId ?: return
    
    try {
        // Aynı kategorideki tüm kanalları al (alfabetik sıralı)
        val channels = contentRepository.getLiveStreamsByCategoryIdSync(currentCategoryId)
        
        if (channels.isEmpty()) {
            timber.log.Timber.w("⚠️ Kategoride kanal bulunamadı: $currentCategoryId")
            return
        }
        
        // Mevcut kanalın pozisyonunu bul
        val currentIndex = channels.indexOfFirst { it.streamId == currentChannelId }
        
        if (currentIndex == -1) {
            timber.log.Timber.w("⚠️ Mevcut kanal listede bulunamadı: $currentChannelId")
            return
        }
        
        // Önceki veya sonraki kanalı belirle
        val newIndex = if (previous) {
            // Önceki kanal (döngüsel: son kanala git)
            if (currentIndex == 0) channels.size - 1 else currentIndex - 1
        } else {
            // Sonraki kanal (döngüsel: ilk kanala git)
            if (currentIndex == channels.size - 1) 0 else currentIndex + 1
        }
        
        val newChannel = channels[newIndex]
        
        timber.log.Timber.d("📺 Kanal değişimi: ${channels[currentIndex].name} → ${newChannel.name}")
        
        // Yeni kanal URL'ini oluştur
        val newUrl = buildLiveStreamUrlUseCase(newChannel)
        
        if (newUrl == null) {
            timber.log.Timber.e("❌ Yeni kanal URL'i oluşturulamadı!")
            Toast.makeText(this, getString(R.string.error_video_url_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Eski kanalın izleme kaydını durdur (canlı yayın için gerekli değil ama temizlik için)
        viewModel.stopWatching()
        
        // Player'ı yeni kanala geçir
        viewModel.playVideo(newUrl, null) // contentId null (canlı yayın)
        
        // Yeni kanalın izleme kaydını başlat
        viewModel.startWatching(newChannel.streamId)
        
        // ChannelId'yi güncelle
        channelId = newChannel.streamId
        
        // PlayerControlView'daki içerik bilgisini güncelle
        binding.playerControlView.setContentInfo(newChannel.name, null)
        
        timber.log.Timber.d("✅ Kanal değişimi tamamlandı: ${newChannel.name}")
        
    } catch (e: Exception) {
        timber.log.Timber.e(e, "❌ Kanal değişimi sırasında hata oluştu")
        Toast.makeText(this, "Kanal değiştirilemedi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
```

**Konum:** Satır 762-830  
**Amaç:** 
- Aynı kategorideki kanalları almak
- Mevcut kanalın pozisyonunu bulmak
- Önceki/sonraki kanala geçmek (döngüsel)
- Player'ı yeni kanala geçirmek
- UI'ı güncellemek

**İşlem Adımları:**
1. `channelId` ve `categoryId` kontrolü
2. Aynı kategorideki kanalları al (`getLiveStreamsByCategoryIdSync`)
3. Mevcut kanalın pozisyonunu bul
4. Önceki/sonraki kanalı belirle (döngüsel)
5. Yeni kanal URL'ini oluştur
6. Eski kanalın izleme kaydını durdur
7. Player'ı yeni kanala geçir (`playVideo`)
8. Yeni kanalın izleme kaydını başlat (`startWatching`)
9. `channelId`'yi güncelle
10. UI'ı güncelle (`setContentInfo`)

---

### 2. LiveStreamsViewModel.kt

#### 2.1. openPlayerEvent Tipi Değiştirildi
```kotlin
// ÖNCE:
private val _openPlayerEvent = MutableSharedFlow<Pair<String, Int>>()
val openPlayerEvent: SharedFlow<Pair<String, Int>> = _openPlayerEvent.asSharedFlow()

// SONRA:
private val _openPlayerEvent = MutableSharedFlow<Triple<String, Int, Int?>>()
val openPlayerEvent: SharedFlow<Triple<String, Int, Int?>> = _openPlayerEvent.asSharedFlow()
```

**Konum:** Satır 204-205  
**Amaç:** Event'e kategori ID'sini eklemek. Triple: (url, channelId, categoryId)

#### 2.2. onChannelSelected() Güncellendi
```kotlin
fun onChannelSelected(channel: LiveStreamEntity) {
    viewModelScope.launch {
        val url = buildLiveStreamUrlUseCase(channel)
        if (url != null) {
            timber.log.Timber.d("📡 CANLI YAYIN URL: $url")
            _openPlayerEvent.emit(Triple(url, channel.streamId, channel.categoryId))
        } else {
            timber.log.Timber.e("❌ Canlı yayın stream URL oluşturulamadı!")
        }
    }
}
```

**Konum:** Satır 213-223  
**Amaç:** Event'e kategori ID'sini eklemek (`channel.categoryId`).

---

### 3. LiveStreamsBrowseFragment.kt

#### 3.1. Import Eklendi
```kotlin
import kotlinx.coroutines.flow.firstOrNull
```

**Konum:** Satır 24  
**Amaç:** Flow'dan değer almak için.

#### 3.2. setupPlayerNavigation() Güncellendi
```kotlin
// ÖNCE:
viewModel.openPlayerEvent.collect { (url, channelId) ->
    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
        putExtra(PlayerActivity.EXTRA_VIDEO_URL, url)
        putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channelId)
    }
    playerActivityLauncher.launch(intent)
}

// SONRA:
viewModel.openPlayerEvent.collect { (url, channelId, categoryId) ->
    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
        putExtra(PlayerActivity.EXTRA_VIDEO_URL, url)
        putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channelId)
        // Kategori ID'sini ekle (kanal değişimi için)
        categoryId?.let {
            putExtra(PlayerActivity.EXTRA_CATEGORY_ID, it)
        }
    }
    playerActivityLauncher.launch(intent)
}
```

**Konum:** Satır 200-213  
**Amaç:** Intent'e kategori ID'sini eklemek.

---

### 4. ContentRepository.kt

#### 4.1. Yeni Suspend Metod Eklendi
```kotlin
suspend fun getLiveStreamsByCategoryIdSync(categoryId: Int): List<LiveStreamEntity> =
    liveStreamRepository.getLiveStreamsByCategoryId(categoryId).firstOrNull() ?: emptyList()
```

**Konum:** Satır 99-100  
**Amaç:** Flow yerine direkt liste döndürmek. `getLiveStreamsByCategoryId()` Flow döndürüyor, bu metod Flow'dan `firstOrNull()` alıp liste döndürüyor.

**Not:** `firstOrNull()` zaten import edilmişti (satır 22).

---

### 5. PlayerControlView.kt

#### 5.1. Canlı Yayın Engellemesi Güncellendi
```kotlin
// ÖNCE:
if (isLiveStream && event.keyCode != KeyEvent.KEYCODE_BACK) {
    // ...
    return false
}

// SONRA:
if (isLiveStream && event.keyCode != KeyEvent.KEYCODE_BACK && 
    event.keyCode != KeyEvent.KEYCODE_PAGE_UP && 
    event.keyCode != KeyEvent.KEYCODE_PAGE_DOWN) {
    // ...
    return false
}
```

**Konum:** Satır 389-400  
**Amaç:** Page Up/Down tuşlarını canlı yayın engellemesinden muaf tutmak. (Güvenlik önlemi - PlayerActivity'de zaten yakalanıyor ama yine de eklendi)

---

## 🔍 Olası Sorunlar ve Analiz

### ⚠️ Gerçek TV'de Çalışmama Sebepleri

#### 1. KeyEvent Kodları Farklı Olabilir
**Sorun:** Bazı TV'lerde Page Up/Down tuşları farklı key code'lar kullanabilir.

**Kontrol Edilmesi Gerekenler:**
- `KeyEvent.KEYCODE_PAGE_UP` = 92
- `KeyEvent.KEYCODE_PAGE_DOWN` = 93

**Çözüm Önerileri:**
- Logcat'te tuş basıldığında hangi key code'un geldiğini kontrol et
- Bazı TV'lerde CH+/CH- tuşları farklı kodlar kullanabilir
- Bazı TV'lerde tuşlar hiç gelmeyebilir (TV firmware sorunu)

#### 2. Tuş Event'i PlayerControlView'a Gidiyor Olabilir
**Sorun:** `onKeyDown()` içinde `return true` yapıyoruz ama event hala PlayerControlView'a gidiyor olabilir.

**Kontrol:**
- `onKeyDown()` içinde log ekleyip tuşun yakalandığını kontrol et
- `changeChannel()` fonksiyonunun çağrıldığını kontrol et

#### 3. categoryId null Olabilir
**Sorun:** Intent'ten `categoryId` gelmemiş olabilir.

**Kontrol:**
- `onCreate()` içinde `categoryId` değerini logla
- `LiveStreamEntity.categoryId` null olabilir (veritabanında kategori ID'si yok)

#### 4. Panel Açıkken Tuş Yakalanmıyor Olabilir
**Sorun:** Panel açıkken tuş event'i panel'e gidiyor olabilir.

**Kontrol:**
- Panel açıkken Page Up/Down basıldığında ne olduğunu kontrol et
- Panel kapatma işlemi çalışıyor mu?

#### 5. Coroutine Scope Sorunu
**Sorun:** `lifecycleScope.launch` kullanıyoruz, Activity lifecycle'ı ile ilgili sorun olabilir.

**Kontrol:**
- `changeChannel()` fonksiyonunun çağrıldığını logla
- Exception yakalanıyor mu kontrol et

---

## 🧪 Test Edilmesi Gerekenler

### 1. Logcat Kontrolleri
```kotlin
// PlayerActivity.onKeyDown() içinde
timber.log.Timber.d("🔍 Page Up/Down tuşu yakalandı: keyCode=$keyCode, channelId=$channelId, categoryId=$categoryId")

// changeChannel() içinde
timber.log.Timber.d("📺 changeChannel çağrıldı: previous=$previous")
timber.log.Timber.d("📺 Kanal listesi: ${channels.size} kanal bulundu")
timber.log.Timber.d("📺 Mevcut kanal pozisyonu: $currentIndex")
```

### 2. Key Code Kontrolü
Gerçek TV'de Page Up/Down tuşuna basıldığında logcat'te hangi key code'un geldiğini kontrol et:
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    timber.log.Timber.d("🔍 TÜM TUŞLAR: keyCode=$keyCode, action=${event?.action}")
    // ...
}
```

### 3. categoryId Kontrolü
```kotlin
// onCreate() içinde
timber.log.Timber.d("🔍 Intent'ten alınan categoryId: $categoryId")
```

### 4. changeChannel() Çağrı Kontrolü
```kotlin
// changeChannel() başında
timber.log.Timber.d("📺 changeChannel() BAŞLADI: previous=$previous, channelId=$channelId, categoryId=$categoryId")
```

---

## 📊 Akış Diyagramı

```
1. Kullanıcı Page Up/Down tuşuna basar
   ↓
2. PlayerActivity.onKeyDown() çağrılır
   ↓
3. channelId != null && categoryId != null kontrolü
   ↓ (true ise)
4. Panel açıksa kapat
   ↓
5. lifecycleScope.launch { changeChannel(...) }
   ↓
6. changeChannel() fonksiyonu:
   - Aynı kategorideki kanalları al
   - Mevcut kanalın pozisyonunu bul
   - Önceki/sonraki kanalı belirle
   - Yeni kanal URL'ini oluştur
   - viewModel.stopWatching()
   - viewModel.playVideo(newUrl, null)
   - viewModel.startWatching(newChannelId)
   - channelId güncelle
   - UI güncelle
   ↓
7. return true (event tüketildi)
```

---

## 🔧 Debug Önerileri

### 1. Tüm Tuşları Logla
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    timber.log.Timber.d("🔍 TUŞ BASILDI: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)}), action=${event?.action}")
    // ... mevcut kod
}
```

### 2. categoryId Kontrolü
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // ...
    categoryId = intent.getIntExtra(EXTRA_CATEGORY_ID, -1).takeIf { it != -1 }
    timber.log.Timber.d("🔍 categoryId: $categoryId, channelId: $channelId")
}
```

### 3. changeChannel() Başında Kontrol
```kotlin
private suspend fun changeChannel(previous: Boolean) {
    timber.log.Timber.d("📺 changeChannel BAŞLADI: previous=$previous")
    timber.log.Timber.d("📺 channelId: $channelId, categoryId: $categoryId")
    // ...
}
```

---

## ✅ Yapılanlar Özeti

1. ✅ PlayerActivity'de Page Up/Down handler eklendi
2. ✅ Intent'e categoryId eklendi
3. ✅ LiveStreamsViewModel'de event'e categoryId eklendi
4. ✅ LiveStreamsBrowseFragment'ta Intent'e categoryId eklendi
5. ✅ ContentRepository'ye suspend metod eklendi
6. ✅ changeChannel() fonksiyonu implement edildi
7. ✅ PlayerControlView'da Page Up/Down bypass edildi
8. ✅ Player üzerinde kalarak kanal değişimi yapılıyor
9. ✅ Panel yönetimi eklendi
10. ✅ Hata yönetimi eklendi

---

## 🎯 Sonuç

Tüm implementasyon tamamlandı. Gerçek TV'de çalışmama sebepleri muhtemelen:
1. Key code farklılığı (bazı TV'ler farklı kodlar kullanabilir)
2. categoryId null geliyor olabilir
3. Tuş event'i farklı bir yerde yakalanıyor olabilir

**Öneri:** Logcat ile detaylı debug yapılmalı ve gerçek TV'de hangi key code'un geldiği kontrol edilmeli.



