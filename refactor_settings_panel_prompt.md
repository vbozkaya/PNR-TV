
# Oyuncu Ayarlar Panelini Yeniden Düzenleme Talimatı

**Ana Hedef:** `PlayerActivity` içinde bulunan dikey ses ve altyazı listelerini, TV arayüzleri için daha modern ve kullanışlı olan yatayda kaydırılabilen karosel (horizontal `RecyclerView`) listelere dönüştür.

---

### Adım 1: Layout Dosyasını Güncelle (`player_settings_panel.xml`)

1.  Proje içinde `player_settings_panel.xml` dosyasını bul.
2.  `recycler_audio_tracks` ve `recycler_subtitle_tracks` adlı `RecyclerView`'ların layout parametrelerini aşağıdaki gibi düzenle:
    *   `android:layout_height` niteliğini `%` bazlı (`layout_constraintHeight_percent`) yerine `wrap_content` olarak değiştir. Bu, listenin yüksekliğinin içerdiği elemanlara göre ayarlanmasını sağlar.
    *   Genişliğin panelin tamamını kaplaması için `android:layout_width` niteliğinin `0dp` (match_constraint) olduğundan emin ol.
3.  Bu `RecyclerView`'ların dikeyde doğru sıralanmasını (`txt_audio_title` -> `recycler_audio_tracks` -> `txt_subtitle_title` -> `recycler_subtitle_tracks` -> `btn_save_settings`) sağlayan `ConstraintLayout` bağlantılarını kontrol et ve koru.

---

### Adım 2: Liste Elemanı Layoutunu Ayarla (`item_track_selection.xml`)

1.  Proje içinde `item_track_selection.xml` dosyasını bul.
2.  Elemanların yatay listede yan yana düzgün durabilmesi için, en dıştaki `ConstraintLayout`'ın genişliğini `match_parent` yerine `wrap_content` veya sabit bir `dp` değeri (örneğin `140dp`) olarak ayarla. Bu, her elemanın kendi içeriği kadar yer kaplamasını veya sabit bir genişliğe sahip olmasını sağlar.
3.  Elemanların içeriğinin (RadioButton ve TextView) bu yeni genişliğe göre ortalanmış ve düzgün göründüğünden emin ol.

---

### Adım 3: `PlayerActivity.kt`'yi Güncelle

1.  `PlayerActivity.kt` dosyasını aç.
2.  `setupSettingsPanel` fonksiyonunu bul.
3.  `audioLayoutManager` ve `subtitleLayoutManager`'ın oluşturulduğu satırları bul.
4.  `LinearLayoutManager`'ın yönünü yatay olarak değiştir.
    *   **Mevcut Hali (benzeri):** `val audioLayoutManager = LinearLayoutManager(this)`
    *   **Olması Gereken:** `val audioLayoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)`
    *   Aynı değişikliği `subtitleLayoutManager` için de yap.

---

### Adım 4: Odak (Focus) Yönetimini Yeniden Düzenle

Bu en kritik adımdır. Odak yönetimi artık dikey (UP/DOWN) yerine öncelikli olarak yatay (LEFT/RIGHT) çalışmalıdır.

1.  **`TrackSelectionAdapter.kt` dosyasını güncelle:**
    *   `TrackViewHolder` içindeki `setOnKeyListener` veya `onFocusChangeListener` mantığını, aynı liste içindeki elemanlar arasında `KEYCODE_DPAD_LEFT` ve `KEYCODE_DPAD_RIGHT` tuşlarıyla gezinmeyi sağlayacak şekilde güncelle.
    *   Listenin son elemanından `DPAD_RIGHT` ile çıkmaya çalışıldığında veya ilk elemanından `DPAD_LEFT` ile çıkmaya çalışıldığında odağın aynı listede kalmasını sağla (döngü yapma).

2.  **`PlayerActivity.kt`'deki `onKeyDown` fonksiyonunu güncelle:**
    *   `KEYCODE_DPAD_DOWN` tuşuna basıldığında odağın şu akışı takip etmesini sağla:
        *   `recycler_audio_tracks` -> `recycler_subtitle_tracks`
        *   `recycler_subtitle_tracks` -> `btn_save_settings`
    *   `KEYCODE_DPAD_UP` tuşuna basıldığında odağın ters yönde hareket etmesini sağla:
        *   `btn_save_settings` -> `recycler_subtitle_tracks`
        *   `recycler_subtitle_tracks` -> `recycler_audio_tracks`
    *   Mevcut döngüsel yapıyı koru: `btn_save_settings` üzerindeyken `KEYCODE_DPAD_DOWN` tuşuna basıldığında, odağın tekrar `recycler_audio_tracks`'in o an seçili olan elemanına atlamasını sağla.

---

### Adım 5: Otomatik Kaydırma (Auto-Scroll) Mantığını Gözden Geçir

1.  `setupRecyclerViewFocusScroll` fonksiyonunu ve `TrackSelectionAdapter` içindeki odaklandığında kaydırma yapan mantığı kontrol et.
2.  Bu mantık `scrollToPositionWithOffset` kullanarak dikey kaydırma için tasarlanmıştı. Yatay kaydırmada da odağa gelen elemanın tamamen görünür hale gelmesini sağladığından emin ol. `scrollToPosition` veya benzeri bir metodun kullanılması gerekebilir. Temel amaç, odak değiştiğinde elemanın ekran dışındaysa görünür alana getirilmesidir.

**Sonuç:** Bu adımlar tamamlandığında, kullanıcı ayarlar panelindeki ses ve altyazı seçeneklerini modern TV uygulamalarında olduğu gibi yatayda kaydırarak seçebilmelidir. Odak yönetimi, bu yeni düzene uygun olarak sorunsuz çalışmalıdır.
