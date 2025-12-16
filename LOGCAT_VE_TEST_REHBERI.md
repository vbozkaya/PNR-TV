# Logcat ve Test Rehberi

## 📱 Gerçek TV'den Logcat'e Bağlanma

### Yöntem 1: USB ile Bağlanma (Önerilen)

1. **TV'yi USB ile PC'ye bağla:**
   - TV'nin USB portuna USB kablosu tak
   - PC'ye bağla
   - TV'de "USB Debugging" veya "Developer Options" açık olmalı

2. **ADB bağlantısını kontrol et:**
   ```bash
   adb devices
   ```
   - TV listede görünmeli: `TV_MODEL_ID    device`

3. **Logcat'i filtrele:**
   ```bash
   adb logcat | grep -E "PlayerActivity|PlayerViewModel|Page|Kanal|categoryId|channelId"
   ```

### Yöntem 2: WiFi ile Bağlanma (ADB over Network)

1. **TV ve PC aynı WiFi ağında olmalı**

2. **TV'nin IP adresini bul:**
   - TV'de: Settings > Network > WiFi > IP Address
   - Örnek: `192.168.1.100`

3. **ADB'yi WiFi'ye bağla:**
   ```bash
   adb connect 192.168.1.100:5555
   ```

4. **Bağlantıyı kontrol et:**
   ```bash
   adb devices
   ```

5. **Logcat'i filtrele:**
   ```bash
   adb logcat | grep -E "PlayerActivity|PlayerViewModel|Page|Kanal"
   ```

### Yöntem 3: Android Studio'dan

1. **Android Studio'yu aç**

2. **Logcat penceresini aç:**
   - Alt kısımda "Logcat" sekmesi
   - Veya: View > Tool Windows > Logcat

3. **Cihaz seç:**
   - Üst kısımda cihaz dropdown'ından TV'yi seç

4. **Filtre uygula:**
   - Arama kutusuna: `PlayerActivity|PlayerViewModel|Page|Kanal|categoryId`

---

## 🖥️ Emülatörde Test Etme

### PC Klavyesi ile Page Up/Down Tuşları

**İYİ HABER:** PC klavyesindeki Page Up/Down tuşları emülatörde çalışır! 🎉

**Nasıl Çalışır:**
- Emülatör açıkken PC klavyesindeki tuşlar direkt emülatöre gider
- Page Up/Down tuşları Android'de `KEYCODE_PAGE_UP` ve `KEYCODE_PAGE_DOWN` olarak algılanır
- DPad tuşları da çalışır (Ok tuşları)

**Test Adımları:**
1. Android Studio'da emülatörü başlat
2. Uygulamayı yükle ve canlı kanal aç
3. PC klavyesinde Page Up/Down tuşlarına bas
4. Logcat'te tuşların yakalandığını kontrol et

### Emülatörde Logcat

1. **Android Studio'da Logcat penceresini aç**
2. **Emülatörü seç** (cihaz dropdown'ından)
3. **Filtre uygula:**
   ```
   PlayerActivity|PlayerViewModel|Page|Kanal|categoryId|channelId
   ```

---

## 🔍 Debug Logları Ekleme

### 1. PlayerActivity.onCreate() - categoryId Kontrolü

```kotlin
// PlayerActivity.kt - onCreate() içinde, satır 88'den sonra
timber.log.Timber.d("🔍 onCreate - channelId: $channelId, categoryId: $categoryId")
timber.log.Timber.d("🔍 Intent'ten alınan categoryId: ${intent.getIntExtra(EXTRA_CATEGORY_ID, -1)}")
```

### 2. PlayerActivity.onKeyDown() - Tüm Tuşları Logla

```kotlin
// PlayerActivity.kt - onKeyDown() başında
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    timber.log.Timber.d("🔍 TUŞ BASILDI: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)})")
    timber.log.Timber.d("🔍 channelId: $channelId, categoryId: $categoryId")
    timber.log.Timber.d("🔍 isPanelOpen: ${settingsPanelBinding.playerSettingsPanel.visibility == View.VISIBLE}")
    
    val isPanelOpen = settingsPanelBinding.playerSettingsPanel.visibility == View.VISIBLE
    // ... mevcut kod
}
```

### 3. PlayerViewModel.seekToNextChannel() - Playlist Kontrolü

```kotlin
// PlayerViewModel.kt - seekToNextChannel() içinde
fun seekToNextChannel(): Boolean {
    timber.log.Timber.d("🔍 seekToNextChannel çağrıldı")
    timber.log.Timber.d("🔍 Player null mu: ${player == null}")
    timber.log.Timber.d("🔍 MediaItem sayısı: ${player?.mediaItemCount}")
    timber.log.Timber.d("🔍 Mevcut MediaItem index: ${player?.currentMediaItemIndex}")
    timber.log.Timber.d("🔍 hasNextMediaItem: ${player?.hasNextMediaItem()}")
    
    val canSeek = player?.hasNextMediaItem() == true
    // ... mevcut kod
}
```

### 4. PlayerActivity.onCreate() - Playlist Oluşturma Kontrolü

```kotlin
// PlayerActivity.kt - onCreate() içinde, playlist oluşturma bölümünde
if (channelId != null && categoryId != null) {
    lifecycleScope.launch {
        try {
            timber.log.Timber.d("🔍 Playlist oluşturuluyor: categoryId=$categoryId")
            val channels = contentRepository.getLiveStreamsByCategoryIdSync(categoryId!!)
            timber.log.Timber.d("🔍 Kategoride ${channels.size} kanal bulundu")
            
            // ...
            
            val mediaItems = viewModel.createPlaylistFromChannels(channels, buildLiveStreamUrlUseCase)
            timber.log.Timber.d("🔍 Playlist oluşturuldu: ${mediaItems.size} MediaItem")
            
            viewModel.playPlaylist(mediaItems, startIndex, null)
            timber.log.Timber.d("✅ Playlist ile player başlatıldı")
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Playlist oluşturulurken hata")
        }
    }
} else {
    timber.log.Timber.w("⚠️ Playlist oluşturulamadı: channelId=$channelId, categoryId=$categoryId")
}
```

---

## 🧪 Test Senaryoları

### Senaryo 1: Emülatörde Test (PC Klavyesi)

1. **Emülatörü başlat**
2. **Uygulamayı yükle**
3. **Canlı kanal aç**
4. **PC klavyesinde Page Up/Down bas**
5. **Logcat'te kontrol et:**
   - Tuş yakalandı mı?
   - categoryId null mu?
   - Playlist var mı?

### Senaryo 2: Gerçek TV'de Test

1. **TV'yi USB veya WiFi ile bağla**
2. **ADB bağlantısını kontrol et**
3. **Logcat'i filtrele**
4. **Canlı kanal aç**
5. **TV kumandasında Page Up/Down bas**
6. **Logcat'te kontrol et:**
   - Hangi key code geliyor?
   - Tuş yakalandı mı?
   - categoryId null mu?

---

## 📊 Logcat Filtre Örnekleri

### Tüm İlgili Loglar
```
PlayerActivity|PlayerViewModel|Page|Kanal|categoryId|channelId|Playlist
```

### Sadece Tuş Olayları
```
TUŞ|keyCode|KEYCODE_PAGE
```

### Sadece Playlist
```
Playlist|MediaItem|setMediaItems
```

### Sadece Hatalar
```
❌|ERROR|Exception
```

---

## 🔧 ADB Komutları

### Cihazları Listele
```bash
adb devices
```

### Logcat'i Temizle
```bash
adb logcat -c
```

### Logcat'i Dosyaya Kaydet
```bash
adb logcat > logcat.txt
```

### Belirli Tag'leri Filtrele
```bash
adb logcat -s PlayerActivity:* PlayerViewModel:* Timber:*
```

### Logcat'i Filtrele ve Dosyaya Kaydet
```bash
adb logcat | grep -E "PlayerActivity|PlayerViewModel|Page|Kanal" > debug_log.txt
```

---

## ⚠️ Önemli Notlar

1. **USB Debugging:** TV'de USB debugging açık olmalı
2. **Developer Options:** TV'de developer options açık olmalı
3. **Aynı Ağ:** WiFi ile bağlanıyorsan TV ve PC aynı ağda olmalı
4. **Firewall:** Firewall ADB bağlantısını engelliyor olabilir

---

## 🎯 Hızlı Test Checklist

- [ ] Emülatörde PC klavyesi ile Page Up/Down test edildi
- [ ] Logcat'te tuşların yakalandığı görüldü
- [ ] categoryId değeri logcat'te kontrol edildi
- [ ] Playlist oluşturma logları kontrol edildi
- [ ] Gerçek TV'de test edildi (opsiyonel)
- [ ] Gerçek TV'de key code kontrol edildi (opsiyonel)

---

## 💡 İpuçları

1. **Emülatörde test et:** PC klavyesi ile hızlı test yapabilirsin
2. **Logcat'i filtrele:** Sadece ilgili logları gör
3. **Logları kaydet:** Önemli testleri dosyaya kaydet
4. **Key code kontrolü:** Gerçek TV'de hangi key code geldiğini öğren



