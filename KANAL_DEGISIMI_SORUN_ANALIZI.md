# Kanal Değişimi Sorunu - Detaylı Analiz Raporu

## 📋 Sorun Özeti

**Sorun:** Canlı kanallarda player açıkken Page Up/Down tuşlarıyla kanal değiştirilemiyor.

**fix_autoplay_robust_prompt.md'nin Kapsamı:**
- Bu dosya sadece **otomatik oynatma sorununu** çözmeye yönelik
- `play()` yerine `playWhenReady = true` kullanımını öneriyor
- **Kanal değiştirme sorununu çözmez**, sadece kanal değiştiğinde video otomatik başlamıyor sorununu çözer

## 🔍 Olası Sorun Nedenleri

### 1. ⚠️ **categoryId NULL Geliyor Olabilir** (EN OLASI)

**Kontrol Edilmesi Gereken:**
- `LiveStreamEntity.categoryId` null olabilir
- Intent'e categoryId eklenirken null kontrolü var ama entity'de null ise eklenmiyor

**Kod İncelemesi:**
```kotlin
// LiveStreamsBrowseFragment.kt - Satır 210
categoryId?.let {
    putExtra(PlayerActivity.EXTRA_CATEGORY_ID, it)
}
```

**Sorun:** Eğer `channel.categoryId` null ise, Intent'e eklenmiyor ve PlayerActivity'de `categoryId` null kalıyor.

**PlayerActivity'de Kontrol:**
```kotlin
// Satır 88
categoryId = intent.getIntExtra(EXTRA_CATEGORY_ID, -1).takeIf { it != -1 }

// Satır 343 - Page Up/Down handler
if (channelId != null && categoryId != null) {  // categoryId null ise bu blok çalışmaz!
    when (keyCode) {
        KeyEvent.KEYCODE_PAGE_UP -> { ... }
        KeyEvent.KEYCODE_PAGE_DOWN -> { ... }
    }
}
```

**Çözüm:** categoryId null kontrolü yapılmalı ve logcat'te kontrol edilmeli.

---

### 2. ⚠️ **Playlist Oluşturulmuyor Olabilir**

**Kontrol Edilmesi Gereken:**
- Playlist oluşturma async bir işlem (coroutine içinde)
- Hata durumunda fallback'e düşüyor ve normal `playVideo` kullanılıyor
- Normal `playVideo` kullanıldığında playlist yok, dolayısıyla `seekToNextMediaItem()` çalışmaz

**Kod İncelemesi:**
```kotlin
// PlayerActivity.kt - Satır 148-195
if (channelId != null && categoryId != null) {
    lifecycleScope.launch {
        try {
            val channels = contentRepository.getLiveStreamsByCategoryIdSync(categoryId!!)
            // ...
            val mediaItems = viewModel.createPlaylistFromChannels(channels, buildLiveStreamUrlUseCase)
            // ...
            viewModel.playPlaylist(mediaItems, startIndex, null)
        } catch (e: Exception) {
            // Fallback: Normal playVideo kullan
            viewModel.playVideo(videoUrl, contentId)  // ⚠️ Playlist yok!
        }
    }
}
```

**Sorun:** 
- Eğer playlist oluşturulurken hata olursa, fallback'e düşüyor
- Fallback'te normal `playVideo` kullanılıyor (playlist yok)
- `seekToNextMediaItem()` sadece playlist varsa çalışır

**Çözüm:** Logcat'te playlist oluşturma başarılı mı kontrol edilmeli.

---

### 3. ⚠️ **Key Code Farklı Olabilir**

**Kontrol Edilmesi Gereken:**
- Bazı TV'lerde Page Up/Down tuşları farklı key code'lar kullanabilir
- CH+/CH- tuşları farklı kodlar olabilir

**Mevcut Kod:**
```kotlin
// PlayerActivity.kt - Satır 345, 359
KeyEvent.KEYCODE_PAGE_UP   // = 92
KeyEvent.KEYCODE_PAGE_DOWN // = 93
```

**Sorun:** TV'de bu tuşlar farklı key code gönderiyor olabilir.

**Çözüm:** Logcat'te tüm tuşları loglayıp hangi key code'un geldiğini kontrol etmek.

---

### 4. ⚠️ **onKeyDown Hiç Çağrılmıyor Olabilir**

**Kontrol Edilmesi Gereken:**
- Event başka bir yerde yakalanıyor olabilir
- PlayerControlView dispatchKeyEvent önce çağrılıyor olabilir

**Kod İncelemesi:**
```kotlin
// PlayerControlView.kt - Satır 389-402
if (isLiveStream && event.keyCode != KeyEvent.KEYCODE_BACK && 
    event.keyCode != KeyEvent.KEYCODE_PAGE_UP && 
    event.keyCode != KeyEvent.KEYCODE_PAGE_DOWN) {
    return false  // ⚠️ Page Up/Down bypass edilmiş ama yine de kontrol edilmeli
}
```

**Sorun:** 
- PlayerControlView dispatchKeyEvent önce çağrılıyor olabilir
- Event tüketiliyor olabilir

**Çözüm:** Logcat'te onKeyDown çağrılıyor mu kontrol edilmeli.

---

### 5. ⚠️ **hasNextMediaItem/hasPreviousMediaItem False Dönüyor**

**Kontrol Edilmesi Gereken:**
- Playlist oluşturulmuş ama player'a set edilmemiş olabilir
- MediaItem'lar yanlış oluşturulmuş olabilir

**Kod İncelemesi:**
```kotlin
// PlayerViewModel.kt - Satır 275-285
fun seekToNextChannel(): Boolean {
    val canSeek = player?.hasNextMediaItem() == true  // ⚠️ False dönüyor olabilir
    if (canSeek) {
        player?.seekToNextMediaItem()
        // ...
    }
    return canSeek
}
```

**Sorun:** 
- Playlist oluşturulmuş ama player'a set edilmemiş
- Veya playlist'te sadece 1 kanal var (hasNextMediaItem false)

**Çözüm:** Logcat'te playlist boyutu ve hasNextMediaItem değeri kontrol edilmeli.

---

## 🧪 Debug Önerileri

### 1. categoryId Kontrolü
```kotlin
// PlayerActivity.onCreate() içinde
timber.log.Timber.d("🔍 onCreate - channelId: $channelId, categoryId: $categoryId")
```

### 2. Tüm Tuşları Logla
```kotlin
// PlayerActivity.onKeyDown() başında
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    timber.log.Timber.d("🔍 TUŞ BASILDI: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)})")
    timber.log.Timber.d("🔍 channelId: $channelId, categoryId: $categoryId")
    // ...
}
```

### 3. Playlist Oluşturma Kontrolü
```kotlin
// PlayerActivity.onCreate() içinde
timber.log.Timber.d("🔍 Playlist oluşturuluyor: categoryId=$categoryId")
// ...
timber.log.Timber.d("🔍 Playlist oluşturuldu: ${mediaItems.size} kanal")
```

### 4. seekToNextChannel Kontrolü
```kotlin
// PlayerViewModel.seekToNextChannel() içinde
fun seekToNextChannel(): Boolean {
    timber.log.Timber.d("🔍 seekToNextChannel çağrıldı")
    timber.log.Timber.d("🔍 Player null mu: ${player == null}")
    timber.log.Timber.d("🔍 MediaItem sayısı: ${player?.mediaItemCount}")
    timber.log.Timber.d("🔍 hasNextMediaItem: ${player?.hasNextMediaItem()}")
    // ...
}
```

---

## 📊 Sorun Tespit Matrisi

| Sorun | Olasılık | Kontrol Yöntemi | Çözüm |
|-------|----------|----------------|-------|
| categoryId null | ⭐⭐⭐⭐⭐ YÜKSEK | Logcat'te categoryId kontrolü | categoryId null ise entity'den al veya hata göster |
| Playlist oluşturulmuyor | ⭐⭐⭐⭐ ORTA | Logcat'te playlist oluşturma kontrolü | Hata mesajlarını kontrol et, fallback'e düşüyor mu? |
| Key code farklı | ⭐⭐⭐ ORTA | Logcat'te tüm tuşları logla | TV'deki gerçek key code'u bul |
| onKeyDown çağrılmıyor | ⭐⭐ DÜŞÜK | Logcat'te onKeyDown logu | Event başka yerde yakalanıyor mu? |
| hasNextMediaItem false | ⭐⭐ DÜŞÜK | Logcat'te playlist boyutu | Playlist doğru oluşturulmuş mu? |

---

## 🎯 Sonuç ve Öneriler

### fix_autoplay_robust_prompt.md Bu Sorunu Çözer mi?

**HAYIR** - Bu dosya sadece otomatik oynatma sorununu çözer. Kanal değiştirme sorununu çözmez.

### En Olası Sorun

**categoryId NULL geliyor olabilir** - Bu durumda:
- `if (channelId != null && categoryId != null)` kontrolü false döner
- Page Up/Down handler'ı hiç çalışmaz
- Tuşlar yakalanmaz

### Yapılması Gerekenler

1. **Logcat ile debug yap:**
   - onCreate'te categoryId değerini logla
   - onKeyDown'da tüm tuşları logla
   - seekToNextChannel'da playlist durumunu logla

2. **categoryId null kontrolü:**
   - LiveStreamEntity.categoryId null ise ne yapılacak?
   - Veritabanında kategori ID'si var mı?

3. **Playlist oluşturma kontrolü:**
   - Playlist başarıyla oluşturuluyor mu?
   - Fallback'e düşüyor mu?

4. **Key code kontrolü:**
   - TV'de hangi key code geliyor?
   - Page Up/Down yerine başka tuşlar mı kullanılıyor?

---

## 🔧 Hızlı Test Adımları

1. **Logcat'i aç ve filtrele:** `PlayerActivity|PlayerViewModel|Page|Kanal`

2. **Canlı kanal aç ve Page Up/Down bas:**
   - onCreate'te categoryId loglanıyor mu?
   - onKeyDown çağrılıyor mu?
   - Hangi key code geliyor?

3. **Playlist durumunu kontrol et:**
   - "Playlist oluşturuldu" logu var mı?
   - Kaç kanal var?
   - hasNextMediaItem true mu?

Bu kontroller yapıldıktan sonra gerçek sorun tespit edilebilir.



