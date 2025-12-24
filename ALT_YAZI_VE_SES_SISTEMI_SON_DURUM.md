# Alt Yazı ve Ses Seçim Sistemi - Final Mimarisi ve Çalışma Mantığı

**Oluşturulma Tarihi:** 23 Aralık 2025
**Durum:** Stabil / Çalışıyor
**Son Kritik Düzeltme:** ExoPlayer API kullanımı (`setTrackTypeDisabled`)

Bu doküman, uygulamanın Alt Yazı ve Ses seçim sisteminin çalışan son halini, kritik kod bloklarını ve mimari kararları belgeler. İleride yapılacak geliştirmelerde referans alınmalıdır.

---

## 1. Kritik Teknik Değişiklikler ve Çözümler

### Sorun: Alt Yazı Kapanmama Sorunu
Eski kodda `setRendererDisabled` kullanılıyordu. Bu metod parametre olarak **Renderer Index** (Sıra No) beklerken, koda yanlışlıkla **Track Type** (Tip ID) veriliyordu. Bu yüzden alt yazı kapanmıyor, ExoPlayer "Auto Select" moduna düşüyordu.

### Çözüm: `setTrackTypeDisabled` API'si
Sorun, ExoPlayer'ın track tipine göre işlem yapan API'si kullanılarak çözüldü. Bu API, renderer sırasından bağımsız olarak, belirtilen tipteki (TEXT veya AUDIO) tüm renderer'ları yönetir.

**Doğru Uygulama (PlayerViewModel.kt):**

```kotlin
// 1. Alt Yazıyı KAPATMA (trackInfo == null durumu)
builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
// Not: Override'ları temizlemek de önemlidir
builder.clearSelectionOverrides(androidx.media3.common.C.TRACK_TYPE_TEXT)

// 2. Alt Yazıyı AÇMA/SEÇME (else durumu)
builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
// Seçilen track override olarak eklenir
```

---

## 2. Alt Yazı Sistemi İşleyişi

### UI Tarafı (PlayerActivity)
1.  **Listeleme:** `loadSubtitleTracks` fonksiyonu ViewModel'den track listesini alır.
2.  **"Kapalı" Seçeneği:** Listeye manuel olarak `groupIndex = -1` olan bir "Kapalı" (Off) seçeneği en başa eklenir.
3.  **Seçim:** Kullanıcı bir track seçtiğinde `saveSubtitleSettings` çağrılır.
    - Eğer "Kapalı" seçildiyse ViewModel'e `null` gönderilir.
    - Bir dil seçildiyse ilgili `TrackInfo` objesi gönderilir.

### Logic Tarafı (PlayerViewModel)
`selectSubtitleTrack(trackInfo: TrackInfo?)` fonksiyonu:

*   **Gelen veri `null` ise (KAPATMA):**
    1.  `setTrackTypeDisabled(TEXT, true)` ile Text Renderer kapatılır.
    2.  `clearSelectionOverrides(TEXT)` ile eski seçimler temizlenir.
    3.  `player.prepare()` çağrılarak değişikliklerin ve callback'lerin tetiklenmesi sağlanır.

*   **Gelen veri dolu ise (SEÇME):**
    1.  `setTrackTypeDisabled(TEXT, false)` ile Text Renderer açılır.
    2.  `clearSelectionOverrides(TEXT)` ile temizlik yapılır.
    3.  `TrackSelectionOverride` oluşturulur ve `addOverride` ile yeni dil parametrelere eklenir.

---

## 3. Ses (Audio) Sistemi İşleyişi

### UI Listeleme Mantığı (`distinctBy`)
Ses listesi oluşturulurken `getAudioTracks` fonksiyonunda:
```kotlin
return audioTracks.distinctBy { it.language }
```
kullanılır.
*   **Amaç:** Kullanıcı arayüzünde aynı dilden ("Türkçe", "Türkçe") birden fazla satır görünmesini engellemek ve listeyi sade tutmak.
*   **Sonuç:** Listede her dilden sadece 1 tane görünür.

### Seçim ve Eşleştirme Mantığı
Kullanıcı arayüzdeki tek "Türkçe" seçeneğine tıkladığında, arka planda doğru ses dosyasını bulmak için `selectAudioTrack` içinde özel bir eşleştirme algoritması çalışır:

1.  **Language Eşleşmesi:** İlk kriter dil kodunun (örn: "tr") uyuşmasıdır.
2.  **Label (Etiket) Eşleşmesi:** Eğer `rawLabel` (ham etiket) mevcutsa, buna göre de ikincil kontrol yapılır.
3.  **Fallback:** Eğer tam eşleşme bulunamazsa, dil kodu uyuşan ilk track seçilir.

Bu mantık, UI sadeleştirilmiş olsa bile arka planda doğru track'in (veya en azından aynı dildeki geçerli bir track'in) seçilmesini garanti eder.

---

## 4. Dosya Yapısı ve Sorumluluklar

| Dosya | Sorumluluk | Önemli Fonksiyonlar |
|-------|------------|---------------------|
| **PlayerActivity.kt** | UI Yönetimi, Panel Animasyonları | `showSubtitlePanel`, `loadSubtitleTracks`, `saveSubtitleSettings` |
| **PlayerViewModel.kt** | ExoPlayer Kontrolü, Mantık | `selectSubtitleTrack`, `selectAudioTrack`, `getSubtitleTracks` |
| **TrackSelectionAdapter.kt** | Liste Görünümü, Tıklama Olayları | `onBindViewHolder`, `updateTracks` |
| **TrackInfo.kt** | Veri Modeli | `groupIndex`, `trackIndex`, `isSelected`, `language` |

---

## 5. İleride Dikkat Edilmesi Gerekenler

1.  **ExoPlayer Sürüm Güncellemeleri:** `setTrackTypeDisabled` gibi API'ler ExoPlayer'ın yeni sürümlerinde değişebilir. Güncelleme yapılırsa bu fonksiyonlar kontrol edilmelidir.
2.  **Seslerdeki `distinctBy`:** Eğer ileride kullanıcıların "Türkçe 5.1" ve "Türkçe Stereo" gibi aynı dildeki farklı formatları seçebilmesi istenirse, `distinctBy { it.language }` satırı kaldırılmalı ve UI buna göre düzenlenmelidir.
3.  **UI Güncellemeleri:** Panel tasarımı şu an klasik yapıdadır. Modern CardView tasarımına geçilmek istenirse `item_track_selection.xml` ve `player_settings_panel.xml` güncellenmelidir (İlgili prompt `CURSOR_UI_UPDATE_PROMPT.txt` dosyasında hazırdır).

Bu doküman, sistemin çalışan kararlı sürümünü temsil eder.
