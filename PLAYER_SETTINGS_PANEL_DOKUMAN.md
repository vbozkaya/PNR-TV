# Player Ayarlar Paneli - Teknik Dokümantasyon

## 📋 İçindekiler

1. [Genel Bakış](#genel-bakış)
2. [Mimari ve Bileşenler](#mimari-ve-bileşenler)
3. [Dosya Yapısı](#dosya-yapısı)
4. [Akış Diyagramı](#akış-diyagramı)
5. [Detaylı Açıklamalar](#detaylı-açıklamalar)
6. [Focus Yönetimi](#focus-yönetimi)
7. [Scroll Mekanizması](#scroll-mekanizması)
8. [Track Seçimi ve Uygulama](#track-seçimi-ve-uygulama)
9. [UI/UX Detayları](#uiux-detayları)
10. [Önemli Notlar](#önemli-notlar)

---

## 🎯 Genel Bakış

Player Ayarlar Paneli, video oynatıcıdaki kontrol bar'da bulunan "Ayarlar" butonuna tıklandığında ekranın sağ tarafından açılan bir paneldir. Bu panel kullanıcıya:

- **Ses dillerini** seçme imkanı sunar
- **Alt yazı dillerini** seçme imkanı sunar (veya alt yazıyı kapatma)
- Seçimlerini kaydetme imkanı sunar

Panel, ExoPlayer (Media3) API'sini kullanarak medya kaynağından gelen ses ve alt yazı track'lerini tespit eder ve kullanıcıya listeler.

---

## 🏗️ Mimari ve Bileşenler

### Temel Bileşenler

```
┌─────────────────────────────────────────────────┐
│              PlayerActivity                      │
│  ┌──────────────────────────────────────────┐   │
│  │      PlayerControlView                   │   │
│  │      - Ayarlar Butonu                    │   │
│  └──────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────┐   │
│  │      Settings Panel                      │   │
│  │  ┌────────────────────────────────────┐  │   │
│  │  │  Ses Dili RecyclerView            │  │   │
│  │  │  - TrackSelectionAdapter          │  │   │
│  │  └────────────────────────────────────┘  │   │
│  │  ┌────────────────────────────────────┐  │   │
│  │  │  Alt Yazı RecyclerView            │  │   │
│  │  │  - TrackSelectionAdapter          │  │   │
│  │  └────────────────────────────────────┘  │   │
│  │  ┌────────────────────────────────────┐  │   │
│  │  │  Kaydet Butonu                    │  │   │
│  │  └────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────┐   │
│  │      PlayerViewModel                      │   │
│  │  - getAudioTracks()                      │   │
│  │  - getSubtitleTracks()                   │   │
│  │  - selectAudioTrack()                    │   │
│  │  - selectSubtitleTrack()                 │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

### Veri Akışı

```
ExoPlayer (currentTracks)
    ↓
PlayerViewModel.getAudioTracks() / getSubtitleTracks()
    ↓
TrackInfo List
    ↓
TrackSelectionAdapter
    ↓
RecyclerView (UI)
    ↓
Kullanıcı Seçimi
    ↓
PlayerViewModel.selectAudioTrack() / selectSubtitleTrack()
    ↓
ExoPlayer TrackSelector (Uygulama)
```

---

## 📁 Dosya Yapısı

### Kotlin Dosyaları

#### 1. `PlayerActivity.kt`
**Konum:** `app/src/main/java/com/pnr/tv/PlayerActivity.kt`

**Sorumluluklar:**
- Panel'in açılması/kapanması (`showSettingsPanel()`, `hideSettingsPanel()`)
- Panel UI bileşenlerinin setup'ı (`setupSettingsPanel()`)
- RecyclerView'ların scroll davranışının ayarlanması (`setupRecyclerViewFocusScroll()`)
- Track'lerin yüklenmesi (`loadTracks()`)
- Seçimlerin kaydedilmesi (`saveSettings()`)
- Focus yönetimi (panel açıkken focus'un panel dışına çıkmaması)

**Önemli Metodlar:**
```kotlin
private fun setupSettingsPanel()           // Panel bileşenlerini başlatır
private fun showSettingsPanel()            // Panel'i gösterir ve animasyon başlatır
private fun hideSettingsPanel()            // Panel'i gizler ve animasyon başlatır
private fun loadTracks()                   // Track'leri yükler ve adapter'lara verir
private fun saveSettings()                 // Seçimleri kaydeder ve panel'i kapatır
private fun setupRecyclerViewFocusScroll() // Scroll davranışını ayarlar
```

#### 2. `PlayerViewModel.kt`
**Konum:** `app/src/main/java/com/pnr/tv/ui/player/PlayerViewModel.kt`

**Sorumluluklar:**
- ExoPlayer'dan track bilgilerini çıkarma
- Track seçimlerini ExoPlayer'a uygulama
- Track label'larını dil kodlarından çevirme

**Önemli Metodlar:**
```kotlin
@UnstableApi fun getAudioTracks(): List<TrackInfo>
    // ExoPlayer'dan ses track'lerini çıkarır ve TrackInfo listesi döner

@UnstableApi fun getSubtitleTracks(): List<TrackInfo>
    // ExoPlayer'dan alt yazı track'lerini çıkarır ve TrackInfo listesi döner

@UnstableApi fun selectAudioTrack(trackInfo: TrackInfo)
    // Seçilen ses track'ini ExoPlayer'a uygular

@UnstableApi fun selectSubtitleTrack(trackInfo: TrackInfo?)
    // Seçilen alt yazı track'ini ExoPlayer'a uygular (null = kapat)
```

#### 3. `TrackSelectionAdapter.kt`
**Konum:** `app/src/main/java/com/pnr/tv/ui/player/TrackSelectionAdapter.kt`

**Sorumluluklar:**
- Track listesini RecyclerView'da gösterme
- Her track item'ı için focus yönetimi
- Focus değiştiğinde otomatik scroll
- RecyclerView'lar arası focus geçişlerini yönetme
- Track seçimi (tıklama veya OK tuşu)

**Önemli Özellikler:**
- `isAudioAdapter`: Adapter'ın ses mi alt yazı mı olduğunu belirler
- `audioRecyclerView` / `subtitleRecyclerView`: RecyclerView'lar arası geçiş için referanslar
- `saveButton`: Kaydet butonuna geçiş için referans

**Önemli Metodlar:**
```kotlin
fun updateTracks(newTracks: List<TrackInfo>, newSelected: TrackInfo?)
    // Track listesini ve seçili track'i günceller

class TrackViewHolder.bind(...)
    // Her item için UI'ı bağlar, focus listener'ları ayarlar
```

#### 4. `TrackInfo.kt`
**Konum:** `app/src/main/java/com/pnr/tv/ui/player/TrackInfo.kt`

**Veri Sınıfı:**
```kotlin
data class TrackInfo(
    val groupIndex: Int,      // TrackGroup indeksi (ExoPlayer'da)
    val trackIndex: Int,      // Track indeksi (TrackGroup içinde)
    val language: String?,    // Dil kodu (örn: "tr", "en")
    val label: String?,       // Görüntülenecek label
    val isSelected: Boolean   // Şu anda seçili mi?
)
```

### Layout Dosyaları

#### 1. `player_settings_panel.xml`
**Konum:** `app/src/main/res/layout/player_settings_panel.xml`

**Yapı:**
```
ConstraintLayout (player_settings_panel)
├── TextView (txt_settings_title) - "Ayarlar" başlığı
├── TextView (txt_audio_title) - "Ses Dili" başlığı
├── RecyclerView (recycler_audio_tracks) - Ses dilleri listesi
│   └── height_percent="0.25" (ekranın %25'i)
├── TextView (txt_subtitle_title) - "Alt Yazılar" başlığı
├── RecyclerView (recycler_subtitle_tracks) - Alt yazılar listesi
│   └── height_percent="0.35" (ekranın %35'i)
└── Button (btn_save_settings) - "Kaydet" butonu
```

**Özellikler:**
- Panel genişliği: `400dp`
- Arka plan: `#E6000000` (yarı saydam siyah)
- Yerleşim: Sağdan açılır
- Elevation: `30dp` (PlayerControlView'ın üstünde görünmesi için)

#### 2. `item_track_selection.xml`
**Konum:** `app/src/main/res/layout/item_track_selection.xml`

**Yapı:**
```
ConstraintLayout (focusable item)
├── RadioButton (radio_track) - Seçim göstergesi
│   └── buttonTint: #00D9FF (cyan)
└── TextView (txt_track_name) - Track ismi
    └── textSize: 12sp
```

**Özellikler:**
- Padding: `4dp` (compact görünüm)
- Min height: `40dp`
- Focus background: `button_focus_background` (cyan border)

### Diğer Dosyalar

#### 1. `button_focus_background.xml`
**Konum:** `app/src/main/res/drawable/button_focus_background.xml`

Focus durumunda cyan border gösteren selector.

#### 2. `slide_in_right.xml` / `slide_out_right.xml`
**Konum:** `app/src/main/res/anim/`

Panel açılış/kapanış animasyonları.

---

## 🔄 Akış Diyagramı

### Panel Açılışı

```
Kullanıcı Ayarlar Butonuna Tıklar
    ↓
PlayerControlView.onSettingsClicked()
    ↓
PlayerActivity.onSettingsClicked()
    ↓
showSettingsPanel()
    ↓
loadTracks()
    ├── PlayerViewModel.getAudioTracks()
    ├── PlayerViewModel.getSubtitleTracks()
    ├── "Kapalı" seçeneği eklenir (alt yazılar için)
    └── Adapter'lara track'ler yüklenir
    ↓
Panel Görünür (VISIBLE)
    ↓
slide_in_right animasyonu başlar
    ↓
İlk ses track'ine focus verilir
```

### Track Seçimi

```
Kullanıcı Track'e Tıklar veya OK Tuşuna Basar
    ↓
TrackViewHolder.onClick() veya onKeyListener
    ↓
onTrackSelected callback çağrılır
    ↓
PlayerActivity'de selectedAudioTrack / selectedSubtitleTrack güncellenir
    ↓
RadioButton.isChecked = true
    ↓
Focus aynı item'da kalır (requestFocus())
```

### Kaydetme

```
Kullanıcı Kaydet Butonuna Tıklar veya OK Tuşuna Basar
    ↓
saveSettings()
    ↓
PlayerViewModel.selectAudioTrack(selectedAudioTrack)
    ├── ExoPlayer trackSelector'a ses track'i uygulanır
    └── TrackSelectionOverride ile override yapılır
    ↓
PlayerViewModel.selectSubtitleTrack(selectedSubtitleTrack)
    ├── Eğer "Kapalı" seçildiyse:
    │   └── Text renderer devre dışı bırakılır
    └── Değilse:
        └── Alt yazı track'i uygulanır
    ↓
hideSettingsPanel()
    ↓
Panel gizlenir (GONE)
```

### Panel Kapanışı

```
Kullanıcı Back Tuşuna Basar (panel açıkken)
    ↓
PlayerActivity.onKeyDown()
    ↓
hideSettingsPanel()
    ↓
slide_out_right animasyonu başlar
    ↓
Animasyon bitince panel GONE olur
```

---

## 📖 Detaylı Açıklamalar

### Track Tespiti (PlayerViewModel)

#### getAudioTracks()

```kotlin
@UnstableApi
fun getAudioTracks(): List<TrackInfo> {
    val tracks = currentTracks ?: return emptyList()
    
    // 1. Tracks.groups içinde TRACK_TYPE_AUDIO olanları filtrele
    // 2. Her trackGroup için format bilgilerini çıkar
    // 3. format.label varsa direkt kullan, yoksa language'dan çevir
    // 4. TrackInfo listesi oluştur
    // 5. distinctBy { it.language } ile aynı dilleri tekrarlama
}
```

**Önemli Noktalar:**
- `currentTracks` ExoPlayer'dan gelir (`player.currentTracks`)
- Her track için `format.label`, `format.language`, `format.id` gibi bilgiler çıkarılır
- Eğer `format.label` null veya boşsa, `getLanguageDisplayName()` ile dil kodu Türkçe'ye çevrilir
- Track'lerin seçili olup olmadığı `group.isSelected && group.isTrackSelected(i)` ile kontrol edilir

#### getSubtitleTracks()

Aynı mantık, ancak `TRACK_TYPE_TEXT` için. Alt yazı track'leri için ek olarak:
- `format.label` önceliklidir (kaynak label'ı direkt kullanılır)
- Örnek: "Spanish [ForcedNarrative]", "English [CC]" gibi özel label'lar direkt gösterilir

#### selectAudioTrack()

```kotlin
@UnstableApi
fun selectAudioTrack(trackInfo: TrackInfo) {
    // 1. TrackGroup'u bul (groupIndex ile)
    // 2. DefaultTrackSelector.Parameters.Builder ile yeni parametreler oluştur
    // 3. clearSelectionOverrides(C.TRACK_TYPE_AUDIO) ile mevcut seçimleri temizle
    // 4. TrackSelectionOverride oluştur (trackGroup, trackIndex)
    // 5. addOverride() ile yeni seçimi ekle
    // 6. trackSelector.parameters = newParameters ile uygula
}
```

#### selectSubtitleTrack()

**Alt yazı kapatma (null):**
```kotlin
builder.setRendererDisabled(C.TRACK_TYPE_TEXT, true)
builder.clearSelectionOverrides(C.TRACK_TYPE_TEXT)
```

**Alt yazı seçme:**
```kotlin
builder.setRendererDisabled(C.TRACK_TYPE_TEXT, false)
builder.clearSelectionOverrides(C.TRACK_TYPE_TEXT)
// TrackSelectionOverride ekle
```

---

## 🎯 Focus Yönetimi

### Focus Chain (Odak Zinciri)

Panel açıkken focus şu sırayla hareket eder:

```
İlk Ses Track
    ↓ (DPAD_DOWN)
İkinci Ses Track
    ↓
...
    ↓
Son Ses Track
    ↓ (DPAD_DOWN)
İlk Alt Yazı Track
    ↓ (DPAD_DOWN)
İkinci Alt Yazı Track
    ↓
...
    ↓
Son Alt Yazı Track
    ↓ (DPAD_DOWN)
Kaydet Butonu
    ↓ (DPAD_DOWN) - DÖNGÜ
İlk Ses Track
```

**Önemli Kurallar:**
1. Panel açıkken focus **ASLA** panel dışına çıkmaz
2. DPAD_UP ile kaydet butonundan yukarı çıkılınca son alt yazı track'ine gidilir
3. DPAD_UP ile ilk ses track'inden yukarı çıkılınca kaydet butonuna gidilir (döngü)
4. DPAD_DOWN ile son alt yazı track'inden aşağı inilince kaydet butonuna gidilir

### Focus Chain Implementasyonu

Focus chain, `TrackSelectionAdapter.TrackViewHolder.bind()` içinde dinamik olarak ayarlanır:

```kotlin
itemView.post {
    // RecyclerView içindeki item'lar için nextFocusUp/Down
    if (!isFirstItem) {
        itemView.nextFocusUpId = prevItem.id
    }
    if (!isLastItem) {
        itemView.nextFocusDownId = nextItem.id
    }
    
    // RecyclerView'lar arası geçişler
    if (isAudioAdapter && isLastItem) {
        // Son ses track'inden ilk alt yazı track'ine
        itemView.nextFocusDownId = firstSubtitleItem.id
    }
    if (!isAudioAdapter && isLastItem) {
        // Son alt yazı track'inden kaydet butonuna
        itemView.nextFocusDownId = saveButton.id
    }
}
```

### Focus Koruması

Panel açıkken, `PlayerActivity.onKeyDown()` içinde focus kontrolü yapılır:

```kotlin
// Panel açıkken focus'un panel dışına çıkmasını engelle
val isFocusInPanel = focusedView?.let { view ->
    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent == settingsPanelBinding.playerSettingsPanel) {
            return@let true
        }
        parent = parent.parent
    }
    false
} ?: false

if (!isFocusInPanel) {
    // Focus'u ilk öğeye geri döndür
    firstAudioTrack?.itemView?.requestFocus()
}
```

### Seçim Sonrası Focus Korunması

Track seçildiğinde focus aynı item'da kalır:

```kotlin
itemView.setOnClickListener {
    val currentFocused = itemView.isFocused
    onClick()
    if (currentFocused) {
        itemView.post {
            itemView.requestFocus()
        }
    }
}
```

---

## 📜 Scroll Mekanizması

### Otomatik Scroll

Focus değiştiğinde, item **mutlaka** görünür alana getirilir. Scroll **animasyonsuz** ve **anında** yapılır.

#### TrackSelectionAdapter'da Scroll

```kotlin
itemView.setOnFocusChangeListener { focusedView, hasFocus ->
    if (hasFocus) {
        val recyclerView = focusedView.parent as? RecyclerView
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        val focusedPosition = recyclerView.getChildAdapterPosition(focusedView)
        
        recyclerView.post {
            // Item görünürlüğünü kontrol et
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            
            // Görünür değilse veya sınırlardaysa scroll yap
            if (focusedPosition < firstVisible || 
                focusedPosition > lastVisible ||
                focusedPosition == firstVisible || 
                focusedPosition == lastVisible) {
                
                // Item'ı üstte konumlandır (20dp padding ile)
                layoutManager.scrollToPositionWithOffset(
                    focusedPosition, 
                    recyclerView.paddingTop + 20
                )
            }
        }
    }
}
```

#### PlayerActivity'de Scroll Listener

RecyclerView'a ek scroll listener eklendi:

```kotlin
recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        // Scroll olduğunda focus'lu item'ın görünürlüğünü kontrol et
        val focusedView = recyclerView.focusedChild
        if (focusedView != null && position görünür değilse) {
            layoutManager.scrollToPositionWithOffset(position, 0)
        }
    }
})
```

### Animasyon Kontrolü

- **itemAnimator = null**: RecyclerView item animasyonları devre dışı
- **smoothScroll kullanılmıyor**: Sadece `scrollToPositionWithOffset()` kullanılıyor
- **post() kullanımı**: Layout işlemlerinden sonra scroll yapılması için

---

## 🎬 Track Seçimi ve Uygulama

### Track Seçimi (UI)

Kullanıcı bir track'e tıkladığında veya OK tuşuna bastığında:

1. `onTrackSelected` callback çağrılır
2. `selectedAudioTrack` veya `selectedSubtitleTrack` güncellenir
3. `notifyDataSetChanged()` ile adapter güncellenir
4. RadioButton'lar yeniden çizilir (seçili olan checked=true)

**Önemli:** Seçim hemen uygulanmaz, sadece hafızada saklanır. "Kaydet" butonuna basılınca uygulanır.

### Track Uygulama (ExoPlayer)

#### Ses Track Uygulama

```kotlin
// 1. Mevcut parametreleri al
val parameters = trackSelector.parameters.buildUpon()
    .setRendererDisabled(C.TRACK_TYPE_AUDIO, false)  // Renderer aktif
    .clearSelectionOverrides(C.TRACK_TYPE_AUDIO)      // Eski seçimleri temizle
    .build()

// 2. Yeni override oluştur
val override = TrackSelectionOverride(trackGroup, trackIndex)

// 3. Override'ı ekle ve uygula
val newParameters = parameters.buildUpon()
    .addOverride(override)
    .build()

trackSelector.parameters = newParameters
```

#### Alt Yazı Track Uygulama

**Kapatma:**
```kotlin
builder.setRendererDisabled(C.TRACK_TYPE_TEXT, true)
builder.clearSelectionOverrides(C.TRACK_TYPE_TEXT)
```

**Seçme:**
```kotlin
builder.setRendererDisabled(C.TRACK_TYPE_TEXT, false)
builder.clearSelectionOverrides(C.TRACK_TYPE_TEXT)
builder.addOverride(TrackSelectionOverride(trackGroup, trackIndex))
```

### "Kapalı" Seçeneği

Alt yazılar için özel bir "Kapalı" seçeneği eklenir:

```kotlin
val closedOption = TrackInfo(
    groupIndex = -1,      // Geçersiz (özel durum)
    trackIndex = -1,      // Geçersiz (özel durum)
    language = null,
    label = "Kapalı",
    isSelected = true/false
)
```

Seçildiğinde `selectSubtitleTrack(null)` çağrılır.

---

## 🎨 UI/UX Detayları

### Panel Tasarımı

- **Genişlik:** 400dp
- **Yerleşim:** Ekranın sağ tarafından açılır
- **Arka Plan:** #E6000000 (yarı saydam siyah, %90 opacity)
- **Elevation:** 30dp (PlayerControlView'ın üstünde)
- **Padding:** 24dp

### Font Boyutları

- **"Ayarlar" başlığı:** 15sp (bold)
- **"Ses Dili" / "Alt Yazılar" başlıkları:** 12sp (bold)
- **Track isimleri:** 12sp
- **"Kaydet" butonu:** 12sp (bold)

### RecyclerView Boyutları

- **Ses dilleri:** Ekranın %25'i (height_percent="0.25")
- **Alt yazılar:** Ekranın %35'i (height_percent="0.35")
- **Min height:** 100dp (her ikisi için)

### Item Tasarımı

- **Padding:** 4dp (compact)
- **Min height:** 40dp
- **RadioButton rengi:** #00D9FF (cyan)
- **Focus border:** 2dp cyan border (button_focus_background)

### Animasyonlar

#### Panel Açılışı

**Dosya:** `app/src/main/res/anim/slide_in_right.xml`

```xml
<translate
    android:fromXDelta="100%"
    android:toXDelta="0%"
    android:duration="300" />
```

Panel sağdan kayarak açılır.

#### Panel Kapanışı

**Dosya:** `app/src/main/res/anim/slide_out_right.xml`

```xml
<translate
    android:fromXDelta="0%"
    android:toXDelta="100%"
    android:duration="300" />
```

Panel sağa doğru kayarak kapanır.

---

## ⚠️ Önemli Notlar

### 1. @UnstableApi Kullanımı

Media3 API'si henüz stabil değil, bu yüzden `@UnstableApi` annotation'ı kullanılır:
- `getAudioTracks()`
- `getSubtitleTracks()`
- `selectAudioTrack()`
- `selectSubtitleTrack()`

Bu metodlar gelecekte değişebilir.

### 2. Track Tespiti

- Track'ler `player.currentTracks` üzerinden alınır
- `currentTracks` null olabilir (track'ler henüz yüklenmemişse)
- Track'ler `Tracks.groups` içinde `TrackGroup` olarak saklanır
- Her `TrackGroup` içinde birden fazla `Format` olabilir

### 3. Label Önceliği

**Ses track'leri:**
1. `format.label` (kaynak label'ı)
2. `getLanguageDisplayName(format.language)` (dil kodundan çeviri)
3. "Bilinmeyen" (hiçbiri yoksa)

**Alt yazı track'leri:**
1. `format.label` (kaynak label'ı) - **Öncelikli**
2. `getLanguageDisplayName(format.language)`
3. "Bilinmeyen"

Bu sayede "Spanish [ForcedNarrative]", "English [CC]" gibi özel label'lar direkt gösterilir.

### 4. Focus Yönetimi

- Panel açıkken **hiçbir şekilde** focus panel dışına çıkmaz
- Back tuşu panel'i kapatır (PlayerActivity'i kapatmaz)
- DPAD_DOWN/UP tuşları sadece panel içinde hareket eder
- Kaydet butonuna DPAD_DOWN ile basılınca ilk ses track'ine dönülür (döngü)

### 5. Scroll Davranışı

- Scroll **animasyonsuz** yapılır (performans ve UX için)
- Focus değiştiğinde item **mutlaka** görünür alana getirilir
- `scrollToPositionWithOffset(position, paddingTop + 20)` ile 20dp boşluk bırakılır

### 6. Track Seçimi Timing

- Track seçimi **anında** uygulanmaz
- Seçimler hafızada saklanır (`selectedAudioTrack`, `selectedSubtitleTrack`)
- "Kaydet" butonuna basılınca hepsi bir kerede uygulanır
- Bu sayede kullanıcı birden fazla seçim yapabilir ve sonra kaydedebilir

### 7. Logging

Detaylı logging yapılır:
- Track tespiti sırasında ham format bilgileri loglanır
- Track seçimi ve uygulaması loglanır
- Panel açılış/kapanış loglanır

Logcat filtreleme için:
- `🔊` - Ses track'leri
- `📝` - Alt yazı track'leri
- `⚙️` - Panel işlemleri

### 8. RecyclerView Focusable Olmamalı

RecyclerView'ların kendisi focusable değildir:
```kotlin
recyclerAudioTracks.isFocusable = false
recyclerSubtitleTracks.isFocusable = false
```

Sadece içindeki item'lar focusable'dır. Bu sayede focus doğru şekilde yönetilir.

### 9. Panel Kontrol Bar İlişkisi

- Panel açıkken kontrol bar **kaybolmaz** (8 saniye kuralı geçersiz)
- Panel açıkken kontrol bar'ın görünürlüğü korunur
- `PlayerControlView.setSettingsPanelOpenCallback()` ile kontrol bar'a panel durumu bildirilir

### 10. distinctBy Kullanımı

Track'ler `distinctBy { it.language }` ile filtrelenir:
- Aynı dil koduna sahip track'ler tekrar edilmez
- Ancak farklı label'lara sahip track'ler (örn: "Türkçe" ve "Türkçe [CC]") ayrı gösterilir
- Bu mantık Media3'ün track gruplarıyla uyumludur

---

## 🔧 Geliştirme İpuçları

### Yeni Özellik Ekleme

1. **Yeni bir track türü eklemek için:**
   - `PlayerViewModel`'de yeni metod ekle (örn: `getVideoTracks()`)
   - `C.TRACK_TYPE_VIDEO` kullan
   - Adapter ve RecyclerView ekle

2. **Panel tasarımını değiştirmek için:**
   - `player_settings_panel.xml` dosyasını düzenle
   - ConstraintLayout kullanarak yerleşimi ayarla
   - Height_percent değerlerini ihtiyaca göre ayarla

3. **Focus davranışını değiştirmek için:**
   - `TrackSelectionAdapter.TrackViewHolder.bind()` içindeki focus chain mantığını düzenle
   - `PlayerActivity.onKeyDown()` içindeki focus korumasını ayarla

### Debug İçin

1. **Track bilgilerini görmek:**
   - Logcat'te `🔊` veya `📝` ile filtrele
   - `FULL_FORMAT` loglarına bak

2. **Focus sorunlarını debug etmek:**
   - `View.isFocused` ile focus durumunu kontrol et
   - `nextFocusUpId` ve `nextFocusDownId` değerlerini logla

3. **Scroll sorunlarını debug etmek:**
   - `findFirstVisibleItemPosition()` ve `findLastVisibleItemPosition()` değerlerini logla
   - Item'ın görünürlüğünü manuel kontrol et

---

## 📚 Referanslar

- **ExoPlayer Media3:** https://developer.android.com/guide/topics/media/media3
- **DefaultTrackSelector:** https://developer.android.com/reference/androidx/media3/exoplayer/trackselection/DefaultTrackSelector
- **RecyclerView Focus:** https://developer.android.com/guide/topics/ui/ui-events#focushandling

---

## 📝 Versiyon Bilgisi

**Son Güncelleme:** 2025-12-07
**Versiyon:** 1.0
**Hazırlayan:** PNR TV Development Team

---

**Not:** Bu dokümantasyon, mevcut implementasyona göre hazırlanmıştır. Kod değişikliklerinde dokümantasyonun da güncellenmesi gerekmektedir.

