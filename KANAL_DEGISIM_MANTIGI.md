# Kanal Değişim Mantığı - Detaylı Açıklama

## 📋 Genel Bakış

Canlı yayın açıkken **Page Up** tuşuna basıldığında, uygulama ExoPlayer'ın playlist özelliğini kullanarak önceki kanala geçmeye çalışır. Bu işlem birkaç adımdan oluşur.

---

## 🔄 Tam Akış: Page Up Tuşuna Basıldığında

### 1️⃣ **Tuş Yakalama - PlayerControlView.dispatchKeyEvent()**

**Dosya:** `PlayerControlView.kt` (satır 387-421)

**Ne Oluyor:**
- TV remote'dan gelen tuş event'i önce `PlayerControlView` tarafından yakalanır
- `dispatchKeyEvent()` metodu çağrılır
- Canlı yayın modunda (`isLiveStream = true`) çoğu tuş engellenir
- **Ancak Page Up/Down tuşları özel olarak izin verilir:**

```kotlin
// Canlı yayında tüm tuş işlemlerini engelle (BACK ve Page Up/Down hariç)
if (isLiveStream && event.keyCode != KeyEvent.KEYCODE_BACK && 
    event.keyCode != KeyEvent.KEYCODE_PAGE_UP && 
    event.keyCode != KeyEvent.KEYCODE_PAGE_DOWN) {
    return false  // Tuş engellendi
}
// Page Up/Down tuşları buraya ulaşır ve bypass edilir
```

**Sonuç:** Page Up tuşu `PlayerControlView` tarafından engellenmez, `PlayerActivity`'ye iletilir.

---

### 2️⃣ **Tuş İşleme - PlayerActivity.onKeyDown()**

**Dosya:** `PlayerActivity.kt` (satır 385-428)

**Ne Oluyor:**

#### Adım 2.1: Tuş Kontrolü
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    // KeyEvent.KEYCODE_PAGE_UP = 92
    when (keyCode) {
        KeyEvent.KEYCODE_PAGE_UP -> {
            // Page Up işlemi
        }
    }
}
```

#### Adım 2.2: Canlı Yayın Kontrolü
```kotlin
// channelId ve categoryId null olmamalı
if (channelId != null && categoryId != null) {
    // Canlı yayın modu aktif
}
```

**ÖNEMLİ:** Eğer `channelId` veya `categoryId` null ise, kanal değişimi çalışmaz!

#### Adım 2.3: Panel Kontrolü
```kotlin
val isPanelOpen = settingsPanelBinding.playerSettingsPanel.visibility == View.VISIBLE
if (isPanelOpen) {
    // Ayarlar paneli açıksa önce kapat
    hideSubtitlePanel() veya hideAudioPanel()
}
```

#### Adım 2.4: ViewModel'e İletim
```kotlin
val success = viewModel.seekToPreviousChannel()
return true  // Event tüketildi
```

**Sonuç:** `PlayerViewModel.seekToPreviousChannel()` çağrılır.

---

### 3️⃣ **Kanal Değişimi - PlayerViewModel.seekToPreviousChannel()**

**Dosya:** `PlayerViewModel.kt` (satır 320-342)

**Ne Oluyor:**

#### Adım 3.1: Playlist Kontrolü
```kotlin
fun seekToPreviousChannel(): Boolean {
    // Player null mu kontrol et
    if (player == null) return false
    
    // Playlist'te önceki kanal var mı?
    val canSeek = player?.hasPreviousMediaItem() == true
    if (!canSeek) {
        // Playlist başında, geçiş yapılamaz
        return false
    }
}
```

**ÖNEMLİ:** Eğer playlist oluşturulmamışsa veya player null ise, kanal değişimi çalışmaz!

#### Adım 3.2: ExoPlayer'a Geçiş Komutu
```kotlin
// playWhenReady'yi önce ayarla (otomatik oynatma için)
player?.playWhenReady = true

// Önceki kanala geç
player?.seekToPreviousMediaItem()
```

**Ne Yapıyor:**
- `seekToPreviousMediaItem()` ExoPlayer'ın playlist'inde bir önceki MediaItem'a geçer
- Bu işlem **asenkron** olarak çalışır
- ExoPlayer yeni kanalı yüklemeye başlar

**Sonuç:** ExoPlayer playlist'te bir önceki kanala geçmeye başlar.

---

### 4️⃣ **MediaItem Değişikliği - ExoPlayer Listener**

**Dosya:** `PlayerViewModel.kt` (satır 96-100)

**Ne Oluyor:**

ExoPlayer yeni kanala geçtiğinde, `onMediaItemTransition` listener'ı otomatik tetiklenir:

```kotlin
override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    // Yeni MediaItem'ı StateFlow'a gönder
    _currentMediaItem.value = mediaItem
    Timber.d("📺 MediaItem değişti: ${mediaItem?.mediaMetadata?.title}, reason: $reason")
}
```

**Sonuç:** `_currentMediaItem` StateFlow'u yeni MediaItem ile güncellenir.

---

### 5️⃣ **UI Güncelleme - PlayerActivity Observer**

**Dosya:** `PlayerActivity.kt` (satır 348-382)

**Ne Oluyor:**

`currentMediaItem` StateFlow'u değiştiğinde, observer tetiklenir:

```kotlin
lifecycleScope.launch {
    viewModel.currentMediaItem.collect { mediaItem ->
        mediaItem?.let {
            // 1. Kanal adını al
            val channelName = it.mediaMetadata.title?.toString()
            
            // 2. Kanal ID'sini tag'den al
            val channelIdFromTag = viewModel.getCurrentChannelId()
            
            // 3. Kanal değişti mi kontrol et
            val isChannelChanged = channelId != null && channelId != channelIdFromTag
            
            // 4. Eski kanalın izleme kaydını durdur
            viewModel.stopWatching()
            
            // 5. Yeni kanalın izleme kaydını başlat
            viewModel.startWatching(channelIdFromTag)
            
            // 6. UI'ı güncelle (kanal adı)
            binding.playerControlView.setContentInfo(channelName, null)
            
            // 7. channelId'yi güncelle
            channelId = channelIdFromTag
            
            // 8. Kontrol bar'ı göster (kullanıcı değişikliği fark etsin)
            if (isChannelChanged) {
                binding.playerControlView.showControls()
            }
        }
    }
}
```

**Sonuç:** 
- Kanal adı güncellenir
- Kontrol bar gösterilir
- İzleme kaydı güncellenir

---

## ⚠️ Potansiyel Sorunlar ve Kontrol Noktaları

### Sorun 1: `channelId` veya `categoryId` null

**Kontrol:** `PlayerActivity.onCreate()` içinde Intent'ten alınan değerler:
```kotlin
channelId = intent.getIntExtra(EXTRA_CHANNEL_ID, -1).takeIf { it != -1 }
categoryId = intent.getIntExtra(EXTRA_CATEGORY_ID, -1).takeIf { it != -1 }
```

**Çözüm:** Intent'te bu değerlerin gönderildiğinden emin ol.

---

### Sorun 2: Playlist Oluşturulmamış

**Kontrol:** `PlayerActivity.onCreate()` içinde playlist oluşturma:
```kotlin
if (channelId != null && categoryId != null) {
    // Playlist oluşturuluyor mu?
    val channels = contentRepository.getLiveStreamsByCategoryIdSync(categoryId!!)
    val mediaItems = viewModel.createPlaylistFromChannels(channels, buildLiveStreamUrlUseCase)
    viewModel.playPlaylist(mediaItems, startIndex, null)
}
```

**Çözüm:** 
- `getLiveStreamsByCategoryIdSync()` boş liste döndürüyor mu?
- `createPlaylistFromChannels()` başarılı mı?
- `playPlaylist()` çağrıldı mı?

---

### Sorun 3: Player null veya Playlist Boş

**Kontrol:** `PlayerViewModel.seekToPreviousChannel()` içinde:
```kotlin
if (player == null) return false
if (player?.hasPreviousMediaItem() != true) return false
```

**Çözüm:** 
- Player initialize edilmiş mi?
- Playlist'te birden fazla kanal var mı?
- Mevcut kanal playlist'in başında mı? (başta ise önceki kanal yok)

---

### Sorun 4: `onMediaItemTransition` Tetiklenmiyor

**Kontrol:** ExoPlayer listener'ı eklenmiş mi?
```kotlin
player = ExoPlayer.Builder(context)
    .build()
    .apply {
        addListener(playerListener)  // Listener eklenmiş mi?
    }
```

**Çözüm:** Listener'ın eklendiğinden emin ol.

---

### Sorun 5: `currentMediaItem` Observer Çalışmıyor

**Kontrol:** Observer lifecycle'a bağlı mı?
```kotlin
lifecycleScope.launch {
    viewModel.currentMediaItem.collect { ... }
}
```

**Çözüm:** 
- `lifecycleScope` doğru mu?
- Observer aktif mi?
- Flow değer yayınlıyor mu?

---

## 🔍 Debug Checklist

Kanal değişimi çalışmıyorsa şunları kontrol et:

1. ✅ **Intent'te channelId ve categoryId var mı?**
   - Logcat'te: `PlayerActivity onCreate - channelId=X, categoryId=Y`

2. ✅ **Playlist oluşturuldu mu?**
   - Logcat'te: `Playlist oluşturuldu: X MediaItem`

3. ✅ **Player initialize edildi mi?**
   - Logcat'te: `Player playlist ile prepare() çağrıldı`

4. ✅ **Page Up tuşu yakalandı mı?**
   - Logcat'te: `PAGE UP tuşu yakalandı`
   - Toast mesajı görünüyor mu?

5. ✅ **seekToPreviousChannel() çağrıldı mı?**
   - Logcat'te: `seekToPreviousChannel çağrıldı`
   - Logcat'te: `hasPreviousMediaItem: true/false`

6. ✅ **onMediaItemTransition tetiklendi mi?**
   - Logcat'te: `MediaItem değişti: [Kanal Adı]`

7. ✅ **Observer tetiklendi mi?**
   - Logcat'te: `MediaItem değişti: [Kanal Adı] (ID: X)`
   - Kontrol bar gösterildi mi?

---

## 📊 Veri Akışı Özeti

```
TV Remote (Page Up)
    ↓
PlayerControlView.dispatchKeyEvent() [Bypass - izin ver]
    ↓
PlayerActivity.onKeyDown() [KEYCODE_PAGE_UP yakalandı]
    ↓
PlayerViewModel.seekToPreviousChannel() [hasPreviousMediaItem kontrolü]
    ↓
ExoPlayer.seekToPreviousMediaItem() [Playlist'te önceki kanala geç]
    ↓
ExoPlayer.onMediaItemTransition() [Yeni MediaItem]
    ↓
PlayerViewModel._currentMediaItem.value = newMediaItem [StateFlow güncelleme]
    ↓
PlayerActivity Observer [currentMediaItem.collect]
    ↓
UI Güncelleme [Kanal adı, kontrol bar gösterimi]
```

---

## 🎯 Sonuç

Kanal değişimi için **5 kritik nokta** var:

1. **Intent'te channelId ve categoryId olmalı**
2. **Playlist oluşturulmalı (playPlaylist çağrılmalı)**
3. **Player null olmamalı ve playlist'te birden fazla kanal olmalı**
4. **ExoPlayer listener'ı eklenmiş olmalı**
5. **Observer aktif olmalı**

Bu 5 noktadan biri eksikse, kanal değişimi çalışmaz!



